package com.smartqueue.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class QueueCreateRequest {
    @NotBlank private String queueName;
    @NotNull private Long doctorId;
    private Long departmentId;
    private Integer maxCapacity;
}
