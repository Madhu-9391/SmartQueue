package com.smartqueue.dto;
import lombok.*;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PriorityAuditResponse { private Long id; private Long appointmentId; private Integer tokenNumber; private String patientName; private String changedByName; private String previousPriority; private String newPriority; private String reason; private LocalDateTime changedAt; }
