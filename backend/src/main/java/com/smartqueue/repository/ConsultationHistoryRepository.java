package com.smartqueue.repository;
import com.smartqueue.entity.ConsultationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConsultationHistoryRepository extends JpaRepository<ConsultationHistory, Long> {

    @Query("SELECT AVG(c.consultationDurationMinutes) FROM ConsultationHistory c WHERE c.doctor.id = :doctorId")
    Double avgDurationByDoctor(@Param("doctorId") Long doctorId);

    @Query("SELECT AVG(c.consultationDurationMinutes) FROM ConsultationHistory c " +
           "WHERE c.doctor.id = :doctorId AND c.dayOfWeek = :dow AND c.timeSlot = :slot")
    Double avgDurationByDoctorAndTimeSlot(@Param("doctorId") Long doctorId,
                                          @Param("dow") Integer dayOfWeek,
                                          @Param("slot") Integer timeSlot);

    @Query("SELECT COUNT(c) FROM ConsultationHistory c WHERE c.wasEmergencyInterruption = true " +
           "AND c.doctor.id = :doctorId AND c.createdAt >= :since")
    Long countEmergencyInterruptionsSince(@Param("doctorId") Long doctorId, @Param("since") LocalDateTime since);

    List<ConsultationHistory> findByDoctorIdOrderByCreatedAtDesc(Long doctorId);
}
