package com.smartqueue.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppointmentResponse {
    private Long id;
    private String patientName;
    private String doctorName;
    private String doctorSpecialization;
    private Integer tokenNumber;
    private String status;
    private String priority;
    private Integer queuePosition;
    private Integer estimatedWaitMinutes;
    private LocalDateTime predictedVisitTime;
    private Integer predictionConfidence;
    private LocalDateTime lastPredictionUpdated;
    private LocalDateTime appointmentDate;
    private LocalDateTime createdAt;
    private Boolean paymentRequired;
    private String paymentStatus;
}
