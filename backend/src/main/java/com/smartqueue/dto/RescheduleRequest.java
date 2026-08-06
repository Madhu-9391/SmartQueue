package com.smartqueue.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class RescheduleRequest { @NotNull private Long newDoctorId; @NotNull private Long newQueueId; private String priority; private String reason; }
