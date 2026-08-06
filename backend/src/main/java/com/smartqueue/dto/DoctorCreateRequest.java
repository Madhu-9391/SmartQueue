package com.smartqueue.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class DoctorCreateRequest {
    @NotBlank private String name;
    @NotBlank private String specialization;
    @NotNull @Min(5) @Max(60) private Integer avgConsultationTime;
    private String roomNumber;
    private String availabilityStatus;
}
