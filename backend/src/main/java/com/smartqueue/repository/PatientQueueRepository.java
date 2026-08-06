package com.smartqueue.repository;
import com.smartqueue.entity.PatientQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientQueueRepository extends JpaRepository<PatientQueue, Long> {
    List<PatientQueue> findByStatus(PatientQueue.QueueStatus status);

    @Query("SELECT q FROM PatientQueue q WHERE q.doctor.id = :doctorId AND q.status = 'ACTIVE'")
    Optional<PatientQueue> findActiveQueueByDoctorId(@Param("doctorId") Long doctorId);
}
