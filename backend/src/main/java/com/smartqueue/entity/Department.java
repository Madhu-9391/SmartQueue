package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "departments")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Department {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String hospitalName;
    private String floor;

    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
    private List<PatientQueue> queues;
}
