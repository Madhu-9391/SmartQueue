package com.smartqueue.service;

import com.smartqueue.dto.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.websocket.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final PatientQueueRepository queueRepo;
    private final AppointmentRepository appointmentRepo;
    private final DoctorRepository doctorRepo;
    private final AiPredictionService predictionService;
    private final SocketEventPublisher publisher;
    private final NotificationService notifService;

    @Transactional
    public PatientQueue createQueue(QueueCreateRequest req) {
        Doctor doctor = doctorRepo.findById(req.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        // Only one active queue per doctor
        queueRepo.findActiveQueueByDoctorId(doctor.getId()).ifPresent(existing -> {
            throw new RuntimeException("Doctor already has an active queue: " + existing.getQueueName());
        });
        PatientQueue queue = PatientQueue.builder()
                .queueName(req.getQueueName())
                .doctor(doctor)
                .maxCapacity(req.getMaxCapacity() != null ? req.getMaxCapacity() : 50)
                .status(PatientQueue.QueueStatus.ACTIVE)
                .currentToken(0)
                .build();
        return queueRepo.save(queue);
    }

    public List<QueueStatusResponse> getAllQueues() {
        return queueRepo.findAll().stream().map(q -> {
            List<Appointment> waiting = appointmentRepo.findWaitingByQueuePrioritized(q.getId());
            return QueueStatusResponse.builder()
                    .queueId(q.getId())
                    .queueName(q.getQueueName())
                    .doctorName(q.getDoctor().getName())
                    .currentToken(q.getCurrentToken())
                    .totalWaiting(waiting.size())
                    .status(q.getStatus().name())
                    .appointments(waiting.stream().map(this::toResponse).collect(Collectors.toList()))
                    .build();
        }).collect(Collectors.toList());
    }

    public QueueStatusResponse getQueueStatus(Long queueId) {
        PatientQueue queue = queueRepo.findById(queueId)
                .orElseThrow(() -> new RuntimeException("Queue not found"));
        List<Appointment> waiting = appointmentRepo.findWaitingAndActiveByQueueId(queueId);
        return QueueStatusResponse.builder()
                .queueId(queueId)
                .queueName(queue.getQueueName())
                .doctorName(queue.getDoctor().getName())
                .currentToken(queue.getCurrentToken())
                .totalWaiting((int) waiting.stream()
                        .filter(a -> a.getStatus() == Appointment.AppointmentStatus.WAITING).count())
                .status(queue.getStatus().name())
                .appointments(waiting.stream().map(this::toResponse).collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public AppointmentResponse callNextToken(Long queueId) {
        PatientQueue queue = queueRepo.findById(queueId)
                .orElseThrow(() -> new RuntimeException("Queue not found"));
        if (queue.getStatus() != PatientQueue.QueueStatus.ACTIVE) {
            throw new RuntimeException("Queue is not active");
        }

        // Complete current active appointment
        appointmentRepo.findActiveByQueueId(queueId).ifPresent(active -> {
            active.setStatus(Appointment.AppointmentStatus.COMPLETED);
            active.setActualEndTime(LocalDateTime.now());
            appointmentRepo.save(active);
            log.info("Completed appointment {} token {}", active.getId(), active.getTokenNumber());
        });

        // Get next priority patient
        List<Appointment> waiting = appointmentRepo.findWaitingByQueuePrioritized(queueId);
        if (waiting.isEmpty()) throw new RuntimeException("No waiting patients in this queue");

        Appointment next = waiting.get(0);
        next.setStatus(Appointment.AppointmentStatus.ACTIVE);
        next.setActualStartTime(LocalDateTime.now());
        appointmentRepo.save(next);

        queue.setCurrentToken(next.getTokenNumber());
        queueRepo.save(queue);

        // Recalculate remaining ETAs
        List<Appointment> remaining = appointmentRepo.findWaitingByQueuePrioritized(queueId);
        List<Appointment> updated  = predictionService.recalculateQueuePredictions(queueId);
        appointmentRepo.saveAll(updated);

        publisher.publishTokenCalled(queueId, next);
        publisher.publishQueueUpdated(queueId);
        publisher.publishEtaUpdated(queueId);

        notifService.notifyUser(next.getUser().getId(),
                "🔔 Your token T-" + next.getTokenNumber() + " is being called! Please proceed to " +
                next.getDoctor().getRoomNumber(),
                Notification.NotificationType.TOKEN_CALLED);

        return toResponse(next);
    }

    @Transactional
    public QueueStatusResponse updateQueue(Long queueId, QueueUpdateRequest req) {
        PatientQueue queue = queueRepo.findById(queueId)
                .orElseThrow(() -> new RuntimeException("Queue not found"));
        if (req.getQueueName() != null) queue.setQueueName(req.getQueueName());
        if (req.getMaxCapacity() != null) queue.setMaxCapacity(req.getMaxCapacity());
        if (req.getStatus() != null) {
            queue.setStatus(PatientQueue.QueueStatus.valueOf(req.getStatus().toUpperCase()));
        }
        queueRepo.save(queue);
        return getQueueStatus(queueId);
    }

    @Transactional
    public void deleteQueue(Long queueId) {
        PatientQueue queue = queueRepo.findById(queueId)
                .orElseThrow(() -> new RuntimeException("Queue not found"));
        long waiting = appointmentRepo.countWaitingByQueueId(queueId);
        if (waiting > 0) throw new RuntimeException(
                "Cannot delete queue with " + waiting + " waiting patients.");
        queueRepo.delete(queue);
    }

    @Transactional
    public void resetQueue(Long queueId) {
        PatientQueue queue = queueRepo.findById(queueId)
                .orElseThrow(() -> new RuntimeException("Queue not found"));
        // Mark all waiting/active as cancelled
        appointmentRepo.findWaitingAndActiveByQueueId(queueId).forEach(a -> {
            a.setStatus(Appointment.AppointmentStatus.CANCELLED);
            appointmentRepo.save(a);
        });
        queue.setCurrentToken(0);
        queueRepo.save(queue);
        publisher.publishQueueUpdated(queueId);
        log.info("Queue {} reset by admin", queueId);
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .patientName(a.getUser().getName())
                .doctorName(a.getDoctor().getName())
                .doctorSpecialization(a.getDoctor().getSpecialization())
                .tokenNumber(a.getTokenNumber())
                .status(a.getStatus().name())
                .priority(a.getPriority().name())
                .predictedVisitTime(a.getPredictedVisitTime())
                .predictionConfidence(a.getPredictionConfidence())
                .lastPredictionUpdated(a.getLastPredictionUpdated())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
