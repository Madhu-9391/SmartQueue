package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "consultation_history")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ConsultationHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    private LocalDateTime scheduledTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;

    @Column(name = "consultation_duration_minutes")
    private Integer consultationDurationMinutes;

    private String delayReason;
    private Integer dayOfWeek;
    private Integer timeSlot;
    private String patientPriority;

    @Column(name = "was_emergency_interruption")
    private Boolean wasEmergencyInterruption = false;

    @Column(name = "queue_position")
    private Integer queuePosition;

    @Column(name = "no_show_before")
    private Integer noShowBefore = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
