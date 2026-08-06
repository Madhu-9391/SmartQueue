package com.smartqueue.service;

import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notifRepo;
    private final UserRepository userRepo;

    public void notifyUser(Long userId, String message, Notification.NotificationType type) {
        userRepo.findById(userId).ifPresent(user -> {
            Notification notif = Notification.builder()
                    .user(user).message(message).type(type)
                    .status(Notification.NotificationStatus.UNREAD)
                    .build();
            notifRepo.save(notif);
            log.info("Notification sent to user {}: {}", userId, message);
        });
    }

    public List<Notification> getNotifications(String userEmail) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return notifRepo.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public void markAllRead(String userEmail) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        notifRepo.markAllReadForUser(user.getId());
    }
}
