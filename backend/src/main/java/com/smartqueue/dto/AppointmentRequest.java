package com.smartqueue.dto;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
public class AppointmentRequest {
    @NotNull private Long doctorId;
    @NotNull private Long queueId;
    private LocalDateTime appointmentDate;
    private String priority;
}
