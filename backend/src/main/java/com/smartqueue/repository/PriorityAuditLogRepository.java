package com.smartqueue.repository;
import com.smartqueue.entity.PriorityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PriorityAuditLogRepository extends JpaRepository<PriorityAuditLog, Long> {
    List<PriorityAuditLog> findByAppointmentIdOrderByChangedAtDesc(Long appointmentId);
    @Query("SELECT p FROM PriorityAuditLog p WHERE p.changedAt >= :since ORDER BY p.changedAt DESC")
    List<PriorityAuditLog> findAllSince(@Param("since") LocalDateTime since);
    @Query("SELECT COUNT(p) FROM PriorityAuditLog p WHERE p.newPriority = 'EMERGENCY' AND p.changedAt >= :since")
    Long countEmergencyEscalationsSince(@Param("since") LocalDateTime since);
}
