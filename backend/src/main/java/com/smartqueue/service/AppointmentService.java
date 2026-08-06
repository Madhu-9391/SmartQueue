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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepo;
    private final PatientQueueRepository queueRepo;
    private final UserRepository userRepo;
    private final DoctorRepository doctorRepo;
    private final AiPredictionService predictionService;
    private final NotificationDispatchService notifDispatch;
    private final SocketEventPublisher publisher;

    @Value("${app.payment.required:false}") private boolean paymentRequired;
    private static final double CAPACITY_WARN = 0.80;

    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest req, String userEmail) {
        User user     = userRepo.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Doctor doctor = doctorRepo.findById(req.getDoctorId()).orElseThrow(() -> new RuntimeException("Doctor not found"));
        PatientQueue queue = queueRepo.findById(req.getQueueId()).orElseThrow(() -> new RuntimeException("Queue not found"));

        if (queue.getStatus() != PatientQueue.QueueStatus.ACTIVE)
            throw new RuntimeException("This queue is not currently active.");
        if (doctor.getAvailabilityStatus() == Doctor.AvailabilityStatus.OFFLINE)
            throw new RuntimeException("Doctor is currently offline.");

        long waiting = appointmentRepo.countWaitingByQueueId(queue.getId());
        if (waiting >= queue.getMaxCapacity())
            throw new RuntimeException("Queue is at maximum capacity (" + queue.getMaxCapacity() + ").");

        // Capacity warning at 80%
        if ((double)(waiting + 1) / queue.getMaxCapacity() >= CAPACITY_WARN) {
            userRepo.findByRole(User.Role.ADMIN).forEach(admin ->
                notifDispatch.dispatchCapacityWarning(admin.getId(), queue.getQueueName(), (int)(waiting + 1), queue.getMaxCapacity()));
        }

        int token = (int) waiting + 1;
        Appointment.Priority priority = Appointment.Priority.NORMAL;
        if (req.getPriority() != null) {
            try { priority = Appointment.Priority.valueOf(req.getPriority()); } catch (IllegalArgumentException ignored) {}
        }

        Appointment.AppointmentStatus initialStatus = paymentRequired
                ? Appointment.AppointmentStatus.PAYMENT_PENDING
                : Appointment.AppointmentStatus.WAITING;

        Appointment appt = Appointment.builder()
                .user(user).doctor(doctor).queue(queue)
                .appointmentDate(req.getAppointmentDate() != null ? req.getAppointmentDate() : LocalDateTime.now())
                .tokenNumber(token).priority(priority).status(initialStatus)
                .paymentRequired(paymentRequired)
                .paymentStatus(paymentRequired ? Payment.PaymentStatus.PENDING : Payment.PaymentStatus.PAID)
                .rescheduleCount(0).build();
        appt = appointmentRepo.save(appt);

        PredictionResult pred = predictionService.predict(appt);
        appt.setPredictedVisitTime(pred.getPredictedVisitTime());
        appt.setPredictionConfidence(pred.getConfidenceMinutes());
        appt.setLastPredictionUpdated(LocalDateTime.now());
        appt = appointmentRepo.save(appt);

        publisher.publishQueueUpdated(queue.getId());

        String eta = pred.getPredictedVisitTime() != null ? pred.getPredictedVisitTime().toLocalTime().toString() : "TBD";
        notifDispatch.dispatch(user.getId(),
                String.format("Appointment booked! Token T-%02d with %s. Expected: %s", token, doctor.getName(), eta),
                Notification.NotificationType.GENERAL);

        log.info("Booked appt {} token {} for {}", appt.getId(), token, user.getName());
        return buildResponse(appt, pred);
    }

    @Transactional
    public AppointmentResponse rescheduleAppointment(Long appointmentId, RescheduleRequest req, String userEmail) {
        Appointment old = appointmentRepo.findById(appointmentId).orElseThrow(() -> new RuntimeException("Appointment not found"));
        if (!old.getUser().getEmail().equals(userEmail)) throw new RuntimeException("Not your appointment");
        if (old.getStatus() == Appointment.AppointmentStatus.COMPLETED || old.getStatus() == Appointment.AppointmentStatus.ACTIVE)
            throw new RuntimeException("Cannot reschedule a " + old.getStatus() + " appointment.");
        if (old.getRescheduleCount() != null && old.getRescheduleCount() >= 2)
            throw new RuntimeException("Maximum reschedule limit (2) reached.");

        Doctor newDoc  = doctorRepo.findById(req.getNewDoctorId()).orElseThrow(() -> new RuntimeException("Doctor not found"));
        PatientQueue nq = queueRepo.findById(req.getNewQueueId()).orElseThrow(() -> new RuntimeException("Queue not found"));

        old.setStatus(Appointment.AppointmentStatus.CANCELLED);
        old.setCancellationType(Appointment.CancellationType.PATIENT_CANCELLED);
        old.setCancellationReason("Rescheduled" + (req.getReason() != null ? ": " + req.getReason() : ""));
        appointmentRepo.save(old);

        long waiting = appointmentRepo.countWaitingByQueueId(nq.getId());
        Appointment.Priority prio = old.getPriority();
        if (req.getPriority() != null) {
            try { prio = Appointment.Priority.valueOf(req.getPriority()); } catch (IllegalArgumentException ignored) {}
        }

        Appointment newAppt = Appointment.builder()
                .user(old.getUser()).doctor(newDoc).queue(nq)
                .appointmentDate(LocalDateTime.now()).tokenNumber((int) waiting + 1)
                .priority(prio).status(Appointment.AppointmentStatus.WAITING)
                .paymentRequired(old.getPaymentRequired()).paymentStatus(old.getPaymentStatus())
                .rescheduledFromId(old.getId())
                .rescheduleCount((old.getRescheduleCount() != null ? old.getRescheduleCount() : 0) + 1)
                .build();
        newAppt = appointmentRepo.save(newAppt);

        PredictionResult pred = predictionService.predict(newAppt);
        newAppt.setPredictedVisitTime(pred.getPredictedVisitTime());
        newAppt.setPredictionConfidence(pred.getConfidenceMinutes());
        newAppt.setLastPredictionUpdated(LocalDateTime.now());
        newAppt = appointmentRepo.save(newAppt);

        publisher.publishQueueUpdated(old.getQueue().getId());
        publisher.publishQueueUpdated(nq.getId());

        notifDispatch.dispatch(old.getUser().getId(),
                String.format("Appointment rescheduled to %s. New token T-%02d.", newDoc.getName(), newAppt.getTokenNumber()),
                Notification.NotificationType.GENERAL);

        return buildResponse(newAppt, pred);
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, String userEmail, String reason) {
        Appointment appt = appointmentRepo.findById(appointmentId).orElseThrow(() -> new RuntimeException("Appointment not found"));
        if (!appt.getUser().getEmail().equals(userEmail)) throw new RuntimeException("Not your appointment");
        appt.setStatus(Appointment.AppointmentStatus.CANCELLED);
        appt.setCancellationType(Appointment.CancellationType.PATIENT_CANCELLED);
        appt.setCancellationReason(reason);
        appointmentRepo.save(appt);
        List<Appointment> updated = predictionService.recalculateQueuePredictions(appt.getQueue().getId());
        appointmentRepo.saveAll(updated);
        publisher.publishQueueUpdated(appt.getQueue().getId());
        notifDispatch.dispatchCancelled(appt.getUser().getId(), appt.getTokenNumber(), reason);
    }

    public List<AppointmentResponse> getMyAppointments(String userEmail) {
        User user = userRepo.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));
        return appointmentRepo.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(a -> buildResponse(a, null)).collect(Collectors.toList());
    }

    private AppointmentResponse buildResponse(Appointment a, PredictionResult p) {
        return AppointmentResponse.builder()
                .id(a.getId()).patientName(a.getUser().getName())
                .doctorName(a.getDoctor().getName()).doctorSpecialization(a.getDoctor().getSpecialization())
                .tokenNumber(a.getTokenNumber()).status(a.getStatus().name()).priority(a.getPriority().name())
                .predictedVisitTime(a.getPredictedVisitTime()).predictionConfidence(a.getPredictionConfidence())
                .lastPredictionUpdated(a.getLastPredictionUpdated())
                .estimatedWaitMinutes(p != null ? p.getEstimatedWaitMinutes() : null)
                .appointmentDate(a.getAppointmentDate()).createdAt(a.getCreatedAt()).build();
    }
}
