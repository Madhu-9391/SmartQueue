package com.smartqueue.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name="appointments",indexes={
    @Index(name="idx_appt_status",columnList="status"),
    @Index(name="idx_appt_doctor",columnList="doctor_id"),
    @Index(name="idx_appt_queue",columnList="queue_id"),
    @Index(name="idx_appt_queue_status_token",columnList="queue_id,status,token_number"),
    @Index(name="idx_appt_doctor_status",columnList="doctor_id,status"),
    @Index(name="idx_appt_user",columnList="user_id"),
    @Index(name="idx_appt_created",columnList="created_at")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Appointment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="doctor_id",nullable=false) private Doctor doctor;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="queue_id") private PatientQueue queue;
    private LocalDateTime appointmentDate;
    private Integer tokenNumber;
    @Enumerated(EnumType.STRING) private AppointmentStatus status=AppointmentStatus.WAITING;
    @Enumerated(EnumType.STRING) private Priority priority=Priority.NORMAL;
    @Column(name="predicted_visit_time") private LocalDateTime predictedVisitTime;
    @Column(name="prediction_confidence") private Integer predictionConfidence;
    @Column(name="last_prediction_updated") private LocalDateTime lastPredictionUpdated;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    @Column(name="cancellation_reason",length=500) private String cancellationReason;
    @Enumerated(EnumType.STRING) @Column(name="cancellation_type") private CancellationType cancellationType;
    @Column(name="rescheduled_from_id") private Long rescheduledFromId;
    @Column(name="reschedule_count") private Integer rescheduleCount=0;
    @Column(name="payment_required") private Boolean paymentRequired=false;
    @Enumerated(EnumType.STRING) @Column(name="payment_status") private Payment.PaymentStatus paymentStatus=Payment.PaymentStatus.PAID;
    @Column(name="admin_notes",length=500) private String adminNotes;
    @CreationTimestamp private LocalDateTime createdAt;
    public enum AppointmentStatus { WAITING,ACTIVE,COMPLETED,CANCELLED,NO_SHOW,PAYMENT_PENDING }
    public enum Priority           { EMERGENCY,VIP,SENIOR_CITIZEN,NORMAL }
    public enum CancellationType   { PATIENT_CANCELLED,ADMIN_CANCELLED,NO_SHOW_AUTO,DOCTOR_UNAVAILABLE }
}
