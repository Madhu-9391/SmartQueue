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

@Service @RequiredArgsConstructor @Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepo;
    private final AppointmentRepository apptRepo;
    private final NotificationDispatchService notifDispatch;
    private final SocketEventPublisher publisher;

    @Value("${razorpay.key.id:rzp_test_demo}") private String razorpayKeyId;
    @Value("${razorpay.key.secret:demo_secret}") private String razorpayKeySecret;
    @Value("${razorpay.consultation.fee:200.00}") private BigDecimal consultationFee;

    @Transactional
    public PaymentResponse createOrder(Long appointmentId) {
        Appointment appt = apptRepo.findById(appointmentId).orElseThrow(() -> new RuntimeException("Appointment not found"));
        paymentRepo.findByAppointmentId(appointmentId).ifPresent(existing -> {
            if (existing.getStatus() == Payment.PaymentStatus.PENDING)
                throw new RuntimeException("Payment order already exists: " + existing.getRazorpayOrderId());
        });
        try {
            com.razorpay.RazorpayClient client = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
            org.json.JSONObject options = new org.json.JSONObject();
            options.put("amount", consultationFee.multiply(BigDecimal.valueOf(100)).intValue());
            options.put("currency", "INR");
            options.put("receipt", "appt_" + appointmentId);
            options.put("payment_capture", 1);
            com.razorpay.Order order = client.orders.create(options);
            String rzpOrderId = order.get("id");
            Payment payment = Payment.builder().appointment(appt).amount(consultationFee).currency("INR").status(Payment.PaymentStatus.PENDING).razorpayOrderId(rzpOrderId).build();
            paymentRepo.save(payment);
            appt.setPaymentStatus(Payment.PaymentStatus.PENDING);
            apptRepo.save(appt);
            log.info("Razorpay order created: {} for appt {}", rzpOrderId, appointmentId);
            return toResponse(payment);
        } catch (Exception e) {
            log.error("Razorpay failed: {}", e.getMessage());
            appt.setPaymentRequired(false);
            appt.setPaymentStatus(Payment.PaymentStatus.PAID);
            if (appt.getStatus() == Appointment.AppointmentStatus.PAYMENT_PENDING)
                appt.setStatus(Appointment.AppointmentStatus.WAITING);
            apptRepo.save(appt);
            throw new RuntimeException("Payment gateway error. Appointment activated without payment.");
        }
    }

    @Transactional
    public PaymentResponse verifyAndConfirm(PaymentVerifyRequest req) {
        String payload  = req.getRazorpayOrderId() + "|" + req.getRazorpayPaymentId();
        String expected = new org.apache.commons.codec.digest.HmacUtils("HmacSHA256", razorpayKeySecret).hmacHex(payload);
        if (!expected.equals(req.getRazorpaySignature())) throw new RuntimeException("Payment verification failed — invalid signature.");
        Payment payment = paymentRepo.findByRazorpayOrderId(req.getRazorpayOrderId()).orElseThrow(() -> new RuntimeException("Payment record not found"));
        payment.setRazorpayPaymentId(req.getRazorpayPaymentId());
        payment.setRazorpaySignature(req.getRazorpaySignature());
        payment.setStatus(Payment.PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepo.save(payment);
        Appointment appt = payment.getAppointment();
        appt.setPaymentStatus(Payment.PaymentStatus.PAID);
        if (appt.getStatus() == Appointment.AppointmentStatus.PAYMENT_PENDING) appt.setStatus(Appointment.AppointmentStatus.WAITING);
        apptRepo.save(appt);
        notifDispatch.dispatchPaymentConfirmed(appt.getUser().getId(), appt.getTokenNumber(), payment.getAmount().toPlainString());
        if (appt.getQueue() != null) publisher.publishQueueUpdated(appt.getQueue().getId());
        return toResponse(payment);
    }

    public PaymentResponse getByAppointment(Long appointmentId) {
        return toResponse(paymentRepo.findByAppointmentId(appointmentId).orElseThrow(() -> new RuntimeException("No payment found")));
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder().id(p.getId()).appointmentId(p.getAppointment().getId()).amount(p.getAmount()).currency(p.getCurrency()).status(p.getStatus().name()).razorpayOrderId(p.getRazorpayOrderId()).razorpayPaymentId(p.getRazorpayPaymentId()).paidAt(p.getPaidAt()).build();
    }
}
