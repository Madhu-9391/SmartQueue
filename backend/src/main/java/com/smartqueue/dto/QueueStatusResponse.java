package com.smartqueue.dto;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QueueStatusResponse {
    private Long queueId;
    private String queueName;
    private String doctorName;
    private Integer currentToken;
    private Integer totalWaiting;
    private String status;
    private List<AppointmentResponse> appointments;
}
