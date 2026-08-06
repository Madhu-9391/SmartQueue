package com.smartqueue.service;

import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
        dispatch(userId,
            String.format("✅ Payment ₹%s confirmed for Token T-%02d. You are in the queue.", amount, token),
            Notification.NotificationType.GENERAL);
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
}
