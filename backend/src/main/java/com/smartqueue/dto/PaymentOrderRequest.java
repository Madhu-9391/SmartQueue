package com.smartqueue.dto;
import lombok.*;
import java.math.BigDecimal;
@Data @NoArgsConstructor @AllArgsConstructor
public class PaymentOrderRequest { private Long appointmentId; private BigDecimal amount; private String currency; }
