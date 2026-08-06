package com.smartqueue.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="priority_audit_log")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PriorityAuditLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="appointment_id",nullable=false) private Appointment appointment;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="changed_by_user_id") private User changedBy;
    @Enumerated(EnumType.STRING) private Appointment.Priority previousPriority;
    @Enumerated(EnumType.STRING) private Appointment.Priority newPriority;
    private String reason;
    @CreationTimestamp private LocalDateTime changedAt;
}
