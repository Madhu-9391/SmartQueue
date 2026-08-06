package com.smartqueue.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DoctorResponse {
    private Long id;
    private String name;
    private String specialization;
    private Integer avgConsultationTime;
    private String availabilityStatus;
    private String roomNumber;
    private Integer delayMinutes;
    private Integer currentQueueSize;
}
