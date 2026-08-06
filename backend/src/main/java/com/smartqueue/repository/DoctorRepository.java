package com.smartqueue.repository;

import com.smartqueue.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findByAvailabilityStatus(Doctor.AvailabilityStatus status);
    Optional<Doctor> findByLinkedEmail(String email);

    @Query("SELECT d FROM Doctor d WHERE LOWER(d.name) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(d.specialization) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<Doctor> searchDoctors(@Param("q") String query);

    List<Doctor> findBySpecialization(String specialization);
}
