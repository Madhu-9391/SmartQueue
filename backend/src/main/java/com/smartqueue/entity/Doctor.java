package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "doctors")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Doctor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String specialization;

    @Column(name = "avg_consultation_time")
    private Integer avgConsultationTime = 15;

    @Enumerated(EnumType.STRING)
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.AVAILABLE;

    private String roomNumber;

    @Column(name = "delay_minutes")
    private Integer delayMinutes = 0;

    /**
     * BUG 4 FIX: Link doctor entity to their login account email.
     * Set when admin creates a doctor login via /api/admin/users/create-staff.
     */
    @Column(name = "linked_email", unique = true)
    private String linkedEmail;

    @OneToMany(mappedBy = "doctor", fetch = FetchType.LAZY)
    private List<Appointment> appointments;

    public enum AvailabilityStatus { AVAILABLE, BUSY, ON_BREAK, OFFLINE }
}
