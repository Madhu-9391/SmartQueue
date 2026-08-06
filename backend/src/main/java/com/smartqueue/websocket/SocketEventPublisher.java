package com.smartqueue.websocket;

import com.smartqueue.dto.SocketEvent;
import com.smartqueue.entity.Appointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishQueueUpdated(Long queueId) {
        SocketEvent event = SocketEvent.builder()
                .eventType("queue-updated")
                .payload(Map.of("queueId", queueId))
                .timestamp(LocalDateTime.now()).build();
        messagingTemplate.convertAndSend("/topic/queue/" + queueId, event);
        log.debug("Published queue-updated for queue {}", queueId);
    }

    public void publishTokenCalled(Long queueId, Appointment appointment) {
        SocketEvent event = SocketEvent.builder()
                .eventType("token-called")
                .payload(Map.of(
                    "queueId",     queueId,
                    "tokenNumber", appointment.getTokenNumber(),
                    "patientName", appointment.getUser().getName()))
                .timestamp(LocalDateTime.now()).build();
        messagingTemplate.convertAndSend("/topic/token-called/" + queueId, event);
        log.info("Token called: {} for queue {}", appointment.getTokenNumber(), queueId);
    }

    public void publishEtaUpdated(Long queueId) {
        SocketEvent event = SocketEvent.builder()
                .eventType("eta-updated")
                .payload(Map.of("queueId", queueId))
                .timestamp(LocalDateTime.now()).build();
        messagingTemplate.convertAndSend("/topic/eta-updated/" + queueId, event);
    }

    public void publishDoctorDelayed(Long doctorId, int delayMinutes, String reason) {
        SocketEvent event = SocketEvent.builder()
                .eventType("doctor-delayed")
                .payload(Map.of(
                    "doctorId",     doctorId,
                    "delayMinutes", delayMinutes,
                    "reason",       reason != null ? reason : "Unspecified"))
                .timestamp(LocalDateTime.now()).build();
        messagingTemplate.convertAndSend("/topic/doctor-delayed/" + doctorId, event);
        log.info("Doctor {} delayed by {} minutes", doctorId, delayMinutes);
    }

    public void publishPersonalNotification(Long userId, String message) {
        SocketEvent event = SocketEvent.builder()
                .eventType("notification")
                .payload(Map.of("message", message))
                .timestamp(LocalDateTime.now()).build();
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, event);
    }
}
