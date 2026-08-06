package com.smartqueue.dto;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class QueueUpdateRequest {
    private String queueName;
    private String status;
    private Integer maxCapacity;
}
