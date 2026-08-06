package com.smartqueue.dto;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class NotificationRequest { private String channel; private String message; private Long userId; }
