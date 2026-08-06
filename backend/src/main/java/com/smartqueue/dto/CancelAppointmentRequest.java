package com.smartqueue.dto;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class CancelAppointmentRequest { private String reason; private String cancellationType; }
