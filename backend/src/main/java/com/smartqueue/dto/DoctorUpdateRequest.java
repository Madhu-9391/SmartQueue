package com.smartqueue.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class DoctorUpdateRequest {
    private String name;
    private String specialization;
    @Min(5) @Max(60) private Integer avgConsultationTime;
    private String roomNumber;
    private String availabilityStatus;
}
