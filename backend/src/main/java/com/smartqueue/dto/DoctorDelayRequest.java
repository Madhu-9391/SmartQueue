package com.smartqueue.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class DoctorDelayRequest {
    @NotNull private Long doctorId;
    @NotNull @Min(0) @Max(120) private Integer delayMinutes;
    private String reason;
}
