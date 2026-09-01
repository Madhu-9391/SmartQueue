package com.smartqueue.repository;
import com.smartqueue.entity.PatientQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientQueueRepository extends JpaRepository<PatientQueue, Long> {
    List<PatientQueue> findByStatus(PatientQueue.QueueStatus status);

    @Query("SELECT q FROM PatientQueue q JOIN FETCH q.doctor")
    List<PatientQueue> findAllWithDoctor();

    @Query("SELECT q FROM PatientQueue q WHERE q.doctor.id = :doctorId AND q.status = 'ACTIVE'")
    Optional<PatientQueue> findActiveQueueByDoctorId(@Param("doctorId") Long doctorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM PatientQueue q WHERE q.id = :queueId")
    Optional<PatientQueue> findByIdForUpdate(@Param("queueId") Long queueId);
}
