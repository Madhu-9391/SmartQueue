package com.smartqueue.dto;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class AppointmentCancelRequest {
    private String reason;
}
