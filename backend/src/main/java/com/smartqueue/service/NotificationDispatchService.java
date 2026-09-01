package com.smartqueue.service;

import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class NotificationDispatchService {

    private final NotificationRepository notifRepo;
    private final UserRepository userRepo;

    // BUG 8 FIX: Use Optional so Spring doesn't crash when mail is disabled
    private final Optional<JavaMailSender> mailSender;

    @Value("${twilio.enabled:false}")       private boolean twilioEnabled;
    @Value("${twilio.account.sid:}")        private String  twilioSid;
    @Value("${twilio.auth.token:}")         private String  twilioToken;
    @Value("${twilio.phone.number:}")       private String  twilioPhone;
    @Value("${spring.mail.enabled:false}")  private boolean mailEnabled;
    @Value("${spring.mail.from:noreply@smartqueue.com}") private String mailFrom;
    @Value("${spring.mail.username:}")      private String mailUsername;

    public NotificationDispatchService(NotificationRepository notifRepo,
                                       UserRepository userRepo,
                                       Optional<JavaMailSender> mailSender) {
        this.notifRepo  = notifRepo;
        this.userRepo   = userRepo;
        this.mailSender = mailSender;
    }

    // ── Always save to DB ──────────────────────────────────────
    public void saveNotification(User user, String message, Notification.NotificationType type) {
        notifRepo.save(Notification.builder()
                .user(user).message(message).type(type)
                .status(Notification.NotificationStatus.UNREAD)
                .build());
    }

    // ── Full dispatch (DB + SMS + Email) ──────────────────────
    @Async("notificationExecutor")
    public void dispatch(Long userId, String message, Notification.NotificationType type) {
        userRepo.findById(userId).ifPresent(user -> {
            saveNotification(user, message, type);

            if (twilioEnabled && isNotBlank(user.getPhone()))
                sendSms(user.getPhone(), message);

            if (mailEnabled && isNotBlank(user.getEmail()))
                sendEmail(user.getEmail(), buildSubject(type), message);
        });
    }

    @Async("notificationExecutor")
    public void dispatchTokenCalled(Long userId, int token, String doctorName, String room) {
        dispatch(userId,
            String.format("🔔 Your token T-%02d is being called! Proceed to %s (%s).",
                token, doctorName, room != null ? room : "OPD"),
            Notification.NotificationType.TOKEN_CALLED);
    }

    @Async("notificationExecutor")
    public void dispatchEtaUpdated(Long userId, String newEta, int conf) {
        dispatch(userId,
            String.format("⏰ Your consultation time updated to %s (±%d min).", newEta, conf),
            Notification.NotificationType.ETA_UPDATED);
    }

    @Async("notificationExecutor")
    public void dispatchDoctorDelayed(Long userId, String doctorName, int delayMin, String newEta) {
        dispatch(userId,
            String.format("⏰ Dr. %s is delayed by %d min. New expected time: %s.", doctorName, delayMin, newEta),
            Notification.NotificationType.DOCTOR_DELAYED);
    }

    @Async("notificationExecutor")
    public void dispatchCancelled(Long userId, int token, String reason) {
        dispatch(userId,
            String.format("❌ Appointment T-%02d cancelled.%s",
                token, reason != null ? " Reason: " + reason : ""),
            Notification.NotificationType.APPOINTMENT_CANCELLED);
    }

    @Async("notificationExecutor")
    public void dispatchPaymentConfirmed(Long userId, int token, String amount) {
        userRepo.findById(userId).ifPresent(user -> {
            String message = String.format("✅ Payment ₹%s confirmed for Token T-%02d. You are in the queue.", amount, token);
            saveNotification(user, message, Notification.NotificationType.GENERAL);
            if (twilioEnabled && isNotBlank(user.getPhone())) sendSms(user.getPhone(), message);
        });
    }

    /** Sends the payment receipt only after the server has verified Razorpay's signature. */
    @Async("notificationExecutor")
    public void dispatchPaymentReceipt(Appointment appt, Payment payment) {
        if (appt == null || appt.getUser() == null || !mailEnabled || mailSender.isEmpty() || !isNotBlank(appt.getUser().getEmail())) {
            return;
        }
        try {
            String to = appt.getUser().getEmail();
            String date = payment.getPaidAt() == null ? "—" : payment.getPaidAt()
                    .atZone(ZoneId.of("Asia/Kolkata"))
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
            String amount = payment.getAmount() == null ? "0.00" : payment.getAmount().toPlainString();
            String paymentId = payment.getRazorpayPaymentId() == null ? "—" : payment.getRazorpayPaymentId();
            String orderId = payment.getRazorpayOrderId() == null ? "—" : payment.getRazorpayOrderId();

            String html = """
                <div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#0f172a">
                  <div style="padding:24px;border-radius:18px;background:linear-gradient(135deg,#062a2a,#0f4c5c);color:white">
                    <div style="font-size:12px;letter-spacing:2px;opacity:.75">SMARTQUEUE</div>
                    <h1 style="margin:8px 0 4px">Payment Receipt</h1>
                    <div style="opacity:.78">Your appointment payment has been verified.</div>
                  </div>
                  <div style="padding:24px;background:#ffffff">
                    <table style="width:100%;border-collapse:collapse">
                      <tr><td style="padding:9px 0;color:#64748b">Patient</td><td style="padding:9px 0;text-align:right;font-weight:700">%s</td></tr>
                      <tr><td style="padding:9px 0;color:#64748b">Appointment</td><td style="padding:9px 0;text-align:right;font-weight:700">#%d</td></tr>
                      <tr><td style="padding:9px 0;color:#64748b">Token</td><td style="padding:9px 0;text-align:right;font-weight:700">T-%02d</td></tr>
                      <tr><td style="padding:9px 0;color:#64748b">Doctor</td><td style="padding:9px 0;text-align:right;font-weight:700">%s</td></tr>
                      <tr><td style="padding:9px 0;color:#64748b">Amount</td><td style="padding:9px 0;text-align:right;font-weight:800;color:#0f766e">₹%s</td></tr>
                      <tr><td style="padding:9px 0;color:#64748b">Payment ID</td><td style="padding:9px 0;text-align:right;font-size:12px">%s</td></tr>
                      <tr><td style="padding:9px 0;color:#64748b">Order ID</td><td style="padding:9px 0;text-align:right;font-size:12px">%s</td></tr>
                      <tr><td style="padding:9px 0;color:#64748b">Paid at</td><td style="padding:9px 0;text-align:right">%s IST</td></tr>
                    </table>
                    <div style="margin-top:20px;padding:14px;border-radius:12px;background:#ecfeff;color:#155e75">Your payment is confirmed and your appointment is now active in the queue.</div>
                  </div>
                  <div style="padding:16px;text-align:center;color:#94a3b8;font-size:12px">SmartQueue Hospital System · Keep this email for your records.</div>
                </div>
                """.formatted(escape(appt.getUser().getName()), appt.getId(), appt.getTokenNumber(),
                        escape(appt.getDoctor() != null ? appt.getDoctor().getName() : "—"), escape(amount),
                        escape(paymentId), escape(orderId), escape(date));

            var message = mailSender.get().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            String from = isNotBlank(mailFrom) ? mailFrom : mailUsername;
            if (isNotBlank(from)) helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("SmartQueue — Payment Receipt & Appointment Confirmed");
            helper.setText(html, true);
            mailSender.get().send(message);
            log.info("Payment receipt emailed to {} for appointment {}", to, appt.getId());
        } catch (Exception e) {
            log.error("Payment receipt email failed for appointment {}: {}", appt.getId(), e.getMessage());
        }
    }

    @Async("notificationExecutor")
    public void dispatchCapacityWarning(Long adminUserId, String queueName, int cur, int max) {
        int pct = (int)((double) cur / max * 100);
        dispatch(adminUserId,
            String.format("⚠️ Queue \"%s\" is at %d%% capacity (%d/%d). Consider opening a new queue.",
                queueName, pct, cur, max),
            Notification.NotificationType.GENERAL);
    }

    // ─────────────────────────────────────────────────────────
    private void sendSms(String phone, String message) {
        try {
            com.twilio.Twilio.init(twilioSid, twilioToken);
            com.twilio.rest.api.v2010.account.Message.creator(
                    new com.twilio.type.PhoneNumber(phone),
                    new com.twilio.type.PhoneNumber(twilioPhone),
                    message).create();
            log.info("SMS sent to {}", phone);
        } catch (Exception e) {
            log.error("SMS failed to {}: {}", phone, e.getMessage());
        }
    }

    private void sendEmail(String to, String subject, String body) {
        // BUG 8 FIX: Check Optional is present + use configured from address
        if (mailSender.isEmpty()) {
            log.warn("Email not sent to {}: JavaMailSender not configured", to);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            // BUG 8 FIX: from must match authenticated smtp username
            String from = isNotBlank(mailFrom) ? mailFrom : mailUsername;
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body + "\n\n— SmartQueue Hospital System");
            mailSender.get().send(msg);
            log.info("Email sent to {} subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Email failed to {}: {}", to, e.getMessage());
        }
    }

    private String buildSubject(Notification.NotificationType type) {
        return switch (type) {
            case TOKEN_CALLED          -> "SmartQueue — Your token is being called";
            case ETA_UPDATED           -> "SmartQueue — Wait time updated";
            case DOCTOR_DELAYED        -> "SmartQueue — Doctor delay notice";
            case APPOINTMENT_CANCELLED -> "SmartQueue — Appointment cancelled";
            default                    -> "SmartQueue — Notification";
        };
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
