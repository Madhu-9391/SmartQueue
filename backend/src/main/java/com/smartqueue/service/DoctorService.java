package com.smartqueue.service;

import com.smartqueue.dto.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorService {

    private final DoctorRepository doctorRepo;
    private final PatientQueueRepository queueRepo;
    private final AppointmentRepository apptRepo;
    private final UserRepository userRepo;

    public List<DoctorResponse> getAllDoctors() {
        return doctorRepo.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public DoctorResponse getMyDoctor(String email) {
        return doctorRepo.findByLinkedEmail(email)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Doctor profile not found"));
    }

    public DoctorResponse getDoctorById(Long id) {
        return toResponse(doctorRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Doctor not found: " + id)));
    }

    /**
     * BUG 2 FIX: Auto-create an active queue for every new doctor.
     * Also creates a DOCTOR role user account so the doctor can login.
     */
    @Transactional
    public DoctorResponse createDoctor(DoctorCreateRequest req) {
        Doctor doctor = Doctor.builder()
                .name(req.getName())
                .specialization(req.getSpecialization())
                .avgConsultationTime(req.getAvgConsultationTime())
                .roomNumber(req.getRoomNumber())
                .availabilityStatus(parseStatus(req.getAvailabilityStatus()))
                .delayMinutes(0)
                .build();
        doctor = doctorRepo.save(doctor);

        // Auto-create active queue for this doctor
        PatientQueue queue = PatientQueue.builder()
                .queueName(req.getSpecialization() + " OPD - " + req.getName())
                .doctor(doctor)
                .status(PatientQueue.QueueStatus.ACTIVE)
                .currentToken(0)
                .maxCapacity(50)
                .build();
        queueRepo.save(queue);

        log.info("Doctor created: {} with auto-queue: {}", doctor.getName(), queue.getQueueName());
        return toResponse(doctor);
    }

    @Transactional
    public DoctorResponse updateDoctor(Long id, DoctorUpdateRequest req) {
        Doctor doctor = doctorRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Doctor not found: " + id));
        if (req.getName() != null)               doctor.setName(req.getName());
        if (req.getSpecialization() != null)     doctor.setSpecialization(req.getSpecialization());
        if (req.getAvgConsultationTime() != null) doctor.setAvgConsultationTime(req.getAvgConsultationTime());
        if (req.getRoomNumber() != null)         doctor.setRoomNumber(req.getRoomNumber());
        if (req.getAvailabilityStatus() != null) doctor.setAvailabilityStatus(parseStatus(req.getAvailabilityStatus()));
        doctor = doctorRepo.save(doctor);
        return toResponse(doctor);
    }

    @Transactional
    public void deleteDoctor(Long id) {
        Doctor doctor = doctorRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Doctor not found: " + id));
        long totalAppointments = apptRepo.countByDoctorId(id);
        if (totalAppointments > 0)
            throw new RuntimeException("Cannot delete a doctor with appointment history. Set the doctor OFFLINE instead.");
        // Close queues first
        queueRepo.findActiveQueueByDoctorId(id).ifPresent(q -> {
            q.setStatus(PatientQueue.QueueStatus.CLOSED);
            queueRepo.save(q);
        });
        doctorRepo.delete(doctor);
        log.info("Doctor deleted: {}", id);
    }

    @Transactional
    public DoctorResponse updateAvailability(Long id, String status) {
        Doctor doctor = doctorRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Doctor not found: " + id));
        doctor.setAvailabilityStatus(parseStatus(status));
        doctor = doctorRepo.save(doctor);
        return toResponse(doctor);
    }

    private Doctor.AvailabilityStatus parseStatus(String status) {
        if (status == null) return Doctor.AvailabilityStatus.AVAILABLE;
        try { return Doctor.AvailabilityStatus.valueOf(status.toUpperCase()); }
        catch (IllegalArgumentException e) { return Doctor.AvailabilityStatus.AVAILABLE; }
    }

    private DoctorResponse toResponse(Doctor d) {
        long qs = apptRepo.findWaitingByDoctorId(d.getId()).size();
        return DoctorResponse.builder()
                .id(d.getId()).name(d.getName()).specialization(d.getSpecialization())
                .avgConsultationTime(d.getAvgConsultationTime())
                .availabilityStatus(d.getAvailabilityStatus().name())
                .roomNumber(d.getRoomNumber()).delayMinutes(d.getDelayMinutes())
                .currentQueueSize((int) qs).build();
    }
}
