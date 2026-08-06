package com.smartqueue.dto;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SocketEvent {
    private String eventType;
    private Object payload;
    private LocalDateTime timestamp;
}
