package com.smartqueue.dto;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentResponse { private Long id; private Long appointmentId; private BigDecimal amount; private String currency; private String status; private String razorpayOrderId; private String razorpayPaymentId; private LocalDateTime paidAt; }
