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
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).getId().equals(appointment.getId())) {
                queuePosition = i + 1;
                break;
            }
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

    public List<Appointment> recalculateQueuePredictions(Long queueId) {
        List<Appointment> waiting = appointmentRepo.findWaitingByQueuePrioritized(queueId);
        for (Appointment appt : waiting) {
            PredictionResult result = predict(appt);
            appt.setPredictedVisitTime(result.getPredictedVisitTime());
            appt.setPredictionConfidence(result.getConfidenceMinutes());
            appt.setLastPredictionUpdated(LocalDateTime.now());
        }
        return waiting;
    }
}
