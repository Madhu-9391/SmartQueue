package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

/**
 * Named PatientQueue to avoid ambiguity with java.util.Queue.
 */
@Entity
@Table(name = "queues")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PatientQueue {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    private String queueName;

    @Enumerated(EnumType.STRING)
    private QueueStatus status = QueueStatus.ACTIVE;

    @Column(name = "current_token")
    private Integer currentToken = 0;

    @Column(name = "max_capacity")
    private Integer maxCapacity = 50;

    @OneToMany(mappedBy = "queue", fetch = FetchType.LAZY)
    private List<Appointment> appointments;

    public enum QueueStatus { ACTIVE, CLOSED, PAUSED }
}
