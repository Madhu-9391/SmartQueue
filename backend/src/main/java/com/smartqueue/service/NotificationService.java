package com.smartqueue.service;

import com.smartqueue.entity.Notification;
import com.smartqueue.entity.User;
import com.smartqueue.repository.NotificationRepository;
import com.smartqueue.repository.UserRepository;
import jakarta.transaction.Transactional;
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

    public void notifyUser(
            Long userId,
            String message,
            Notification.NotificationType type
    ) {
        userRepo.findById(userId).ifPresent(user -> {
            Notification notif = Notification.builder()
                    .user(user)
                    .message(message)
                    .type(type)
                    .status(Notification.NotificationStatus.UNREAD)
                    .build();

            notifRepo.save(notif);

            log.info(
                    "Notification sent to user {}: {}",
                    userId,
                    message
            );
        });
    }

    public List<Notification> getNotifications(String userEmail) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        return notifRepo.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional
    public void markAllRead(String userEmail) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        notifRepo.markAllReadForUser(user.getId());
    }

    @Transactional
    public void markRead(Long notificationId, String userEmail) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        int updated = notifRepo.markReadForUser(
                notificationId,
                user.getId()
        );

        if (updated == 0) {
            throw new RuntimeException(
                    "Notification not found or does not belong to the current user."
            );
        }
    }
}