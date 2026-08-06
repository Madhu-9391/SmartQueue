package com.smartqueue.service;

import com.smartqueue.dto.PredictionResult;
import com.smartqueue.entity.Appointment;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlServiceClient {

    private final AiPredictionService fallbackService;
    private final ConsultationHistoryRepository historyRepo;
    private final AppointmentRepository appointmentRepo;

    @Value("${app.ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    @Value("${app.ml.service.enabled:false}")
    private boolean mlEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public PredictionResult predict(Appointment appointment) {
        if (!mlEnabled) {
            log.debug("ML service disabled — using Java fallback");
            return fallbackService.predict(appointment);
        }
        try {
            return callMlService(appointment);
        } catch (Exception e) {
            log.warn("ML service unavailable ({}), using Java fallback", e.getMessage());
            return fallbackService.predict(appointment);
        }
    }

    private PredictionResult callMlService(Appointment appointment) {
        LocalDateTime now = LocalDateTime.now();
        Long queueId      = appointment.getQueue().getId();
        Long doctorId     = appointment.getDoctor().getId();

        List<Appointment> waiting = appointmentRepo.findWaitingByQueuePrioritized(queueId);
        int position = 1;
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).getId().equals(appointment.getId())) { position = i + 1; break; }
        }

        Double historicalAvg = historyRepo.avgDurationByDoctorAndTimeSlot(
                doctorId, now.getDayOfWeek().getValue(), now.getHour());
        double doctorSpeed = historicalAvg != null ? historicalAvg
                : (appointment.getDoctor().getAvgConsultationTime() != null
                   ? appointment.getDoctor().getAvgConsultationTime() : 15.0);

        long emergs = historyRepo.countEmergencyInterruptionsSince(
                doctorId, now.toLocalDate().atStartOfDay());
        int delay = appointment.getDoctor().getDelayMinutes() != null
                ? appointment.getDoctor().getDelayMinutes() : 0;

        Map<String, Object> payload = Map.of(
            "doctor_id",              doctorId,
            "queue_position",         position,
            "doctor_avg_speed",       doctorSpeed,
            "doctor_delay_today",     delay,
            "time_of_day",            now.getHour(),
            "day_of_week",            now.getDayOfWeek().getValue(),
            "emergency_cases_before", (int) Math.min(emergs, 10),
            "patient_priority",       appointment.getPriority().name(),
            "no_show_probability",    0.08,
            "department_load",        waiting.size()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                mlServiceUrl + "/predict", new HttpEntity<>(payload, headers), Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<?, ?> body        = response.getBody();
            int waitMins          = ((Number) body.get("predicted_wait_minutes")).intValue();
            int confWindow        = ((Number) body.get("confidence_window")).intValue();
            String isoTime        = (String) body.get("predicted_visit_time");
            LocalDateTime predTime = LocalDateTime.parse(isoTime.substring(0, 19));

            log.info("ML service: appt={} wait={}m conf=+-{}m",
                     appointment.getId(), waitMins, confWindow);

            return PredictionResult.builder()
                    .predictedVisitTime(predTime)
                    .confidenceMinutes(confWindow)
                    .estimatedWaitMinutes(waitMins)
                    .modelConfidenceScore(0.91)
                    .predictionBasis("python-ml-service")
                    .build();
        }
        throw new RuntimeException("ML service returned non-200 status");
    }
}
