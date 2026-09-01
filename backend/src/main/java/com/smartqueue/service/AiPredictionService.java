package com.smartqueue.service;

import com.smartqueue.dto.PredictionResult;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPredictionService {

    private final ConsultationHistoryRepository historyRepo;
    private final AppointmentRepository appointmentRepo;
    private final DoctorRepository doctorRepo;

    private static final double NO_SHOW_RATE           = 0.08;
    private static final int    EMERGENCY_PENALTY_MINS = 12;

    public PredictionResult predict(Appointment appointment) {
        log.debug("Running AI prediction for appointment {}", appointment.getId());

        Doctor doctor = appointment.getDoctor();
        Long doctorId = doctor.getId();
        Long queueId  = appointment.getQueue().getId();

        List<Appointment> waiting = appointmentRepo.findWaitingByQueuePrioritized(queueId);
        int queuePosition = 1;
        int currentRank = priorityRank(appointment.getPriority());
        for (Appointment candidate : waiting) {
            int candidateRank = priorityRank(candidate.getPriority());
            if (candidate.getId().equals(appointment.getId())) break;
            if (candidateRank < currentRank ||
                    (candidateRank == currentRank && candidate.getTokenNumber() != null
                            && appointment.getTokenNumber() != null
                            && candidate.getTokenNumber() < appointment.getTokenNumber())) {
                queuePosition++;
            }
        }

        // PAYMENT_PENDING appointments are not in the waiting query yet, so count all queued patients
        // ahead of the current token instead of incorrectly predicting them as position 1.
        if (waiting.stream().noneMatch(a -> a.getId().equals(appointment.getId()))) {
            queuePosition = 1 + (int) waiting.stream()
                    .filter(a -> {
                        int r = priorityRank(a.getPriority());
                        return r < currentRank || (r == currentRank && a.getTokenNumber() != null
                                && appointment.getTokenNumber() != null && a.getTokenNumber() < appointment.getTokenNumber());
                    }).count();
        }

        LocalDateTime now    = LocalDateTime.now();
        int dayOfWeek        = now.getDayOfWeek().getValue();
        int hour             = now.getHour();

        Double historicalAvg = historyRepo.avgDurationByDoctorAndTimeSlot(doctorId, dayOfWeek, hour);
        double doctorSpeed   = (historicalAvg != null) ? historicalAvg
                : (doctor.getAvgConsultationTime() != null ? doctor.getAvgConsultationTime() : 15.0);

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        long emergencyToday      = historyRepo.countEmergencyInterruptionsSince(doctorId, startOfDay);
        int emergencyCount       = (int) Math.min(emergencyToday, 5);

        int delayMinutes     = doctor.getDelayMinutes() != null ? doctor.getDelayMinutes() : 0;
        double noShowSaving  = queuePosition * NO_SHOW_RATE * doctorSpeed;

        double baseWait         = (queuePosition - 1) * doctorSpeed;
        double emergencyPenalty = emergencyCount * EMERGENCY_PENALTY_MINS;
        double totalWait        = Math.max(2.0, baseWait + emergencyPenalty - noShowSaving + delayMinutes);

        int estimatedWaitMinutes = (int) Math.round(totalWait);
        double variance          = (queuePosition * 0.5) + (emergencyCount * 1.5) + (delayMinutes * 0.3);
        int confidenceMinutes    = (int) Math.max(3, Math.min(20, Math.round(4 + variance)));
        double modelScore        = Math.max(0.5, Math.min(0.99, 1.0 - (variance / 50.0)));

        LocalDateTime predictedTime = now.plusMinutes(estimatedWaitMinutes);

        String featureSummary = String.format(
                "pos=%d, speed=%.1fm, emerg=%d, delay=%dm, noShowSave=%.1fm",
                queuePosition, doctorSpeed, emergencyCount, delayMinutes, noShowSaving);

        log.info("Prediction appt={}: wait={}m conf=+-{}m", appointment.getId(), estimatedWaitMinutes, confidenceMinutes);

        return PredictionResult.builder()
                .predictedVisitTime(predictedTime)
                .confidenceMinutes(confidenceMinutes)
                .estimatedWaitMinutes(estimatedWaitMinutes)
                .modelConfidenceScore(modelScore)
                .predictionBasis(featureSummary)
                .build();
    }

    private int priorityRank(Appointment.Priority priority) {
        return switch (priority) {
            case EMERGENCY -> 0;
            case VIP -> 1;
            case SENIOR_CITIZEN -> 2;
            case NORMAL -> 3;
        };
    }

    public List<Appointment> recalculateQueuePredictions(Long queueId) {
        List<Appointment> waiting = appointmentRepo.findWaitingByQueuePrioritized(queueId);
        if (waiting.isEmpty()) return waiting;

        Appointment first = waiting.get(0);
        Doctor doctor = first.getDoctor();
        LocalDateTime now = LocalDateTime.now();
        int dayOfWeek = now.getDayOfWeek().getValue();
        int hour = now.getHour();
        Double historicalAvg = historyRepo.avgDurationByDoctorAndTimeSlot(doctor.getId(), dayOfWeek, hour);
        double doctorSpeed = historicalAvg != null ? historicalAvg
                : (doctor.getAvgConsultationTime() != null ? doctor.getAvgConsultationTime() : 15.0);
        long emergencyToday = historyRepo.countEmergencyInterruptionsSince(doctor.getId(), now.toLocalDate().atStartOfDay());
        int emergencyCount = (int) Math.min(emergencyToday, 5);
        int delayMinutes = doctor.getDelayMinutes() != null ? doctor.getDelayMinutes() : 0;

        for (int position = 1; position <= waiting.size(); position++) {
            Appointment appt = waiting.get(position - 1);
            double noShowSaving = position * NO_SHOW_RATE * doctorSpeed;
            double baseWait = (position - 1) * doctorSpeed;
            double totalWait = Math.max(2.0, baseWait + emergencyCount * EMERGENCY_PENALTY_MINS - noShowSaving + delayMinutes);
            int estimatedWaitMinutes = (int) Math.round(totalWait);
            double variance = (position * 0.5) + (emergencyCount * 1.5) + (delayMinutes * 0.3);
            int confidenceMinutes = (int) Math.max(3, Math.min(20, Math.round(4 + variance)));
            appt.setPredictedVisitTime(now.plusMinutes(estimatedWaitMinutes));
            appt.setPredictionConfidence(confidenceMinutes);
            appt.setLastPredictionUpdated(now);
        }
        return waiting;
    }
}
