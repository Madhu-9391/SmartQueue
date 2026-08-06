package com.smartqueue.scheduler;

import com.smartqueue.entity.Appointment;
import com.smartqueue.repository.AppointmentRepository;
import com.smartqueue.websocket.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoCleanupScheduler {

    private final AppointmentRepository appointmentRepo;
    private final SocketEventPublisher publisher;

    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void detectNoShows() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        List<Appointment> stale = appointmentRepo.findStaleWaitingAppointments(threshold);
        for (Appointment a : stale) {
            Long queueId = a.getQueue() != null ? a.getQueue().getId() : null;
            a.setStatus(Appointment.AppointmentStatus.NO_SHOW);
            appointmentRepo.save(a);
            if (queueId != null) publisher.publishQueueUpdated(queueId);
            log.info("No-show: appointment {} token {}", a.getId(), a.getTokenNumber());
        }
        if (!stale.isEmpty()) log.info("Auto-cleanup: {} no-show(s) marked", stale.size());
    }
}
