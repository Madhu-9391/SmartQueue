package com.smartqueue.service;
import com.smartqueue.dto.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.websocket.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service @RequiredArgsConstructor @Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepo;
    private final AppointmentRepository apptRepo;
    private final NotificationDispatchService notifDispatch;
    private final SocketEventPublisher publisher;

    @Value("${razorpay.key.id:}") private String razorpayKeyId;
    @Value("${razorpay.key.secret:}") private String razorpayKeySecret;
    @Value("${razorpay.consultation.fee:200.00}") private BigDecimal consultationFee;

    @Transactional
    public PaymentResponse createOrder(Long appointmentId, String userEmail) {
        Appointment appt = apptRepo.findById(appointmentId).orElseThrow(() -> new RuntimeException("Appointment not found"));
        if (appt.getUser() == null || !appt.getUser().getEmail().equalsIgnoreCase(userEmail))
            throw new RuntimeException("Access denied: this appointment does not belong to the current user.");
        if (appt.getStatus() != Appointment.AppointmentStatus.PAYMENT_PENDING)
            throw new RuntimeException("This appointment does not require payment or has already been confirmed.");
        Payment existing = paymentRepo.findByAppointmentId(appointmentId).orElse(null);
        if (existing != null && existing.getStatus() == Payment.PaymentStatus.PAID)
            return toResponse(existing);
        if (existing != null && existing.getStatus() == Payment.PaymentStatus.PENDING)
            throw new RuntimeException("Payment order already exists: " + existing.getRazorpayOrderId());
        if (razorpayKeyId == null || razorpayKeyId.isBlank() || razorpayKeySecret == null || razorpayKeySecret.isBlank())
            throw new RuntimeException("Payment gateway is not configured.");
        try {
            com.razorpay.RazorpayClient client = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
            org.json.JSONObject options = new org.json.JSONObject();
            options.put("amount", consultationFee.multiply(BigDecimal.valueOf(100)).intValue());
            options.put("currency", "INR");
            options.put("receipt", "appt_" + appointmentId);
            options.put("payment_capture", 1);
            com.razorpay.Order order = client.orders.create(options);
            String rzpOrderId = order.get("id");
            Payment payment = existing != null ? existing : Payment.builder().appointment(appt).build();
            payment.setAmount(consultationFee);
            payment.setCurrency("INR");
            payment.setStatus(Payment.PaymentStatus.PENDING);
            payment.setRazorpayOrderId(rzpOrderId);
            payment.setRazorpayPaymentId(null);
            payment.setRazorpaySignature(null);
            payment.setFailureReason(null);
            paymentRepo.save(payment);
            appt.setPaymentStatus(Payment.PaymentStatus.PENDING);
            apptRepo.save(appt);
            log.info("Razorpay order created: {} for appt {}", rzpOrderId, appointmentId);
            return toResponse(payment);
        } catch (Exception e) {
            log.error("Razorpay failed for appointment {}: {}", appointmentId, e.getMessage());
            throw new RuntimeException("Payment gateway error. Please retry payment.");
        }
    }

    @Transactional
    public PaymentResponse verifyAndConfirm(PaymentVerifyRequest req, String userEmail) {
        String payload  = req.getRazorpayOrderId() + "|" + req.getRazorpayPaymentId();
        String expected = new org.apache.commons.codec.digest.HmacUtils("HmacSHA256", razorpayKeySecret).hmacHex(payload);
        if (req.getRazorpaySignature() == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), req.getRazorpaySignature().getBytes(StandardCharsets.US_ASCII))) throw new RuntimeException("Payment verification failed — invalid signature.");
        Payment payment = paymentRepo.findByRazorpayOrderId(req.getRazorpayOrderId()).orElseThrow(() -> new RuntimeException("Payment record not found"));
        Appointment appt = payment.getAppointment();
        if (appt == null || !appt.getId().equals(req.getAppointmentId()))
            throw new RuntimeException("Payment does not belong to the specified appointment.");
        if (appt.getUser() == null || !appt.getUser().getEmail().equalsIgnoreCase(userEmail))
            throw new RuntimeException("Access denied: this payment does not belong to the current user.");
        if (payment.getStatus() == Payment.PaymentStatus.PAID) return toResponse(payment);
        if (!payment.getRazorpayOrderId().equals(req.getRazorpayOrderId()))
            throw new RuntimeException("Payment order mismatch");
        payment.setRazorpayPaymentId(req.getRazorpayPaymentId());
        payment.setRazorpaySignature(req.getRazorpaySignature());
        payment.setStatus(Payment.PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepo.save(payment);
        appt.setPaymentStatus(Payment.PaymentStatus.PAID);
        if (appt.getStatus() == Appointment.AppointmentStatus.PAYMENT_PENDING) appt.setStatus(Appointment.AppointmentStatus.WAITING);
        apptRepo.save(appt);
        notifDispatch.dispatchPaymentConfirmed(appt.getUser().getId(), appt.getTokenNumber(), payment.getAmount().toPlainString());
        notifDispatch.dispatchPaymentReceipt(appt, payment);
        if (appt.getQueue() != null) publisher.publishQueueUpdated(appt.getQueue().getId());
        return toResponse(payment);
    }

    public PaymentResponse getByAppointment(Long appointmentId) {
        return toResponse(paymentRepo.findByAppointmentId(appointmentId).orElseThrow(() -> new RuntimeException("No payment found")));
    }

    public PaymentResponse getByAppointmentForUser(Long appointmentId, String userEmail) {
        Appointment appt = apptRepo.findById(appointmentId).orElseThrow(() -> new RuntimeException("Appointment not found"));
        if (appt.getUser() == null || !appt.getUser().getEmail().equalsIgnoreCase(userEmail))
            throw new RuntimeException("Access denied: this appointment does not belong to the current user.");
        return getByAppointment(appointmentId);
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder().id(p.getId()).appointmentId(p.getAppointment().getId()).amount(p.getAmount()).currency(p.getCurrency()).status(p.getStatus().name()).razorpayOrderId(p.getRazorpayOrderId()).razorpayPaymentId(p.getRazorpayPaymentId()).paidAt(p.getPaidAt()).build();
    }
}
