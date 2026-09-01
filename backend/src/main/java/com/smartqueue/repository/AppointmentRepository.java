package com.smartqueue.repository;
import com.smartqueue.entity.Appointment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT a FROM Appointment a WHERE a.queue.id=:queueId AND a.status='WAITING' ORDER BY CASE a.priority WHEN 'EMERGENCY' THEN 1 WHEN 'VIP' THEN 2 WHEN 'SENIOR_CITIZEN' THEN 3 ELSE 4 END, a.tokenNumber ASC")
    List<Appointment> findWaitingByQueuePrioritized(@Param("queueId") Long queueId);

    @Query("SELECT a FROM Appointment a WHERE a.queue.id=:queueId AND a.status IN ('WAITING','ACTIVE') ORDER BY CASE a.status WHEN 'ACTIVE' THEN 0 ELSE 1 END, CASE a.priority WHEN 'EMERGENCY' THEN 1 WHEN 'VIP' THEN 2 WHEN 'SENIOR_CITIZEN' THEN 3 ELSE 4 END, a.tokenNumber ASC")
    List<Appointment> findWaitingAndActiveByQueueId(@Param("queueId") Long queueId);

    @Query("SELECT a FROM Appointment a WHERE a.queue.id=:queueId AND a.status='ACTIVE'")
    Optional<Appointment> findActiveByQueueId(@Param("queueId") Long queueId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.queue.id=:queueId AND a.status='WAITING'")
    Long countWaitingByQueueId(@Param("queueId") Long queueId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.queue.id=:queueId AND a.status IN ('WAITING','PAYMENT_PENDING')")
    Long countLiveByQueueId(@Param("queueId") Long queueId);

    @Query("SELECT COALESCE(MAX(a.tokenNumber), 0) FROM Appointment a WHERE a.queue.id=:queueId AND a.status IN ('WAITING','ACTIVE','PAYMENT_PENDING')")
    Integer findMaxLiveTokenByQueueId(@Param("queueId") Long queueId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.status='WAITING'")
    Long countAllWaiting();

    long countByDoctorId(Long doctorId);
    long countByUserId(Long userId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.status='COMPLETED' AND a.actualEndTime>=:since")
    Long countAllCompletedSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id=:doctorId AND a.status='COMPLETED' AND a.actualStartTime>=:since")
    Long countCompletedSince(@Param("doctorId") Long doctorId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id=:doctorId AND a.status='COMPLETED' AND a.actualEndTime>=:since")
    Long countCompletedByDoctorSince(@Param("doctorId") Long doctorId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.status='NO_SHOW' AND a.createdAt>=:since")
    Long countNoShowsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id=:doctorId AND a.status='NO_SHOW' AND a.createdAt>=:since")
    Long countNoShowsByDoctorSince(@Param("doctorId") Long doctorId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.createdAt>=:since AND a.status IN ('WAITING','ACTIVE','COMPLETED','CANCELLED','NO_SHOW')")
    Long countTotalSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id=:doctorId AND a.priority='EMERGENCY' AND a.createdAt>=:since")
    Long countEmergenciesByDoctorSince(@Param("doctorId") Long doctorId, @Param("since") LocalDateTime since);

    @Query("SELECT a FROM Appointment a WHERE a.status='COMPLETED' AND a.actualStartTime IS NOT NULL AND a.createdAt IS NOT NULL AND a.actualStartTime>=:since")
    List<Appointment> findCompletedWithTimingsSince(@Param("since") LocalDateTime since);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id=:doctorId AND a.status='WAITING' ORDER BY a.priority ASC, a.tokenNumber ASC")
    List<Appointment> findWaitingByDoctorId(@Param("doctorId") Long doctorId);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id=:doctorId AND a.status IN ('WAITING','ACTIVE') ORDER BY CASE a.priority WHEN 'EMERGENCY' THEN 1 WHEN 'VIP' THEN 2 WHEN 'SENIOR_CITIZEN' THEN 3 ELSE 4 END, a.tokenNumber ASC")
    List<Appointment> findActiveQueueForDoctor(@Param("doctorId") Long doctorId);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.queue WHERE a.status='WAITING' AND a.predictedVisitTime IS NOT NULL AND a.predictedVisitTime<:threshold")
    List<Appointment> findStaleWaitingAppointments(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT a FROM Appointment a WHERE (:status IS NULL OR a.status=:status) AND (:doctorId IS NULL OR a.doctor.id=:doctorId) ORDER BY a.createdAt DESC")
    List<Appointment> findAllFiltered(@Param("status") Appointment.AppointmentStatus status, @Param("doctorId") Long doctorId);

    @Query("SELECT a.priority, COUNT(a) FROM Appointment a WHERE a.createdAt>=:since GROUP BY a.priority")
    List<Object[]> countByPrioritySince(@Param("since") LocalDateTime since);
@Query("""
    SELECT a.createdAt, a.actualStartTime
    FROM Appointment a
    WHERE a.status = 'COMPLETED'
      AND a.actualStartTime IS NOT NULL
      AND a.createdAt IS NOT NULL
      AND a.actualStartTime >= :since
    """)
List<Object[]> findCompletedTimingsSince(
        @Param("since") LocalDateTime since
);
@Query("""
    SELECT d.name,
           d.avgConsultationTime,
           COUNT(a)
    FROM Doctor d
    LEFT JOIN d.appointments a
        ON a.status = 'WAITING'
    GROUP BY d.id, d.name, d.avgConsultationTime
    """)
List<Object[]> findDoctorWaitingLoads();
    @Query(value="SELECT HOUR(a.actual_end_time), COUNT(*) FROM appointments a WHERE a.status='COMPLETED' AND a.actual_end_time>=:since GROUP BY HOUR(a.actual_end_time) ORDER BY HOUR(a.actual_end_time)", nativeQuery=true)
    List<Object[]> countCompletedByHourSince(@Param("since") LocalDateTime since);
}
