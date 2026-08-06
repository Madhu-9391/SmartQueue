package com.smartqueue.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="payments")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="appointment_id",nullable=false) private Appointment appointment;
    @Column(nullable=false,precision=10,scale=2) private BigDecimal amount;
    @Column(nullable=false) private String currency="INR";
    @Enumerated(EnumType.STRING) private PaymentStatus status=PaymentStatus.PENDING;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
    private String failureReason;
    @CreationTimestamp private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    public enum PaymentStatus { PENDING, PAID, FAILED, REFUNDED }
}
