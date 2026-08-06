package com.smartqueue.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class PaymentVerifyRequest { @NotBlank private String razorpayOrderId; @NotBlank private String razorpayPaymentId; @NotBlank private String razorpaySignature; @NotNull private Long appointmentId; }
