package com.smartqueue.controller;

import com.smartqueue.dto.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.service.*;
import com.smartqueue.websocket.SocketEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/kiosk")
@RequiredArgsConstructor
@Tag(name = "Kiosk")
public class KioskController {
    private final UserRepository userRepo;
    private final DoctorRepository doctorRepo;
    private final PatientQueueRepository queueRepo;
    private final AppointmentRepository apptRepo;
    private final SocketEventPublisher publisher;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    @Transactional
    @Operation(summary = "Public walk-in registration for reception kiosk")
    public ResponseEntity<ApiResponse<AppointmentResponse>> register(@Valid @RequestBody KioskRegisterRequest req) {
        User user = userRepo.findByPhone(req.getPhone()).orElseGet(() -> {
            String digits = req.getPhone().replaceAll("[^0-9]", "");
            String email = "kiosk_" + digits + "@smartqueue.local";
            if (userRepo.existsByEmail(email)) return userRepo.findByEmail(email).orElseThrow();
            return userRepo.save(User.builder().name(req.getName()).phone(req.getPhone()).email(email)
                    .password(passwordEncoder.encode("kiosk_no_login_" + digits))
                    .role(User.Role.PATIENT).build());
        });
        if (user.getRole() != User.Role.PATIENT) throw new RuntimeException("This phone number belongs to a non-patient account.");

        Doctor doctor = doctorRepo.findById(req.getDoctorId()).orElseThrow(() -> new RuntimeException("Doctor not found"));
        PatientQueue queue = queueRepo.findByIdForUpdate(req.getQueueId()).orElseThrow(() -> new RuntimeException("Queue not found"));
        if (queue.getDoctor() == null || !queue.getDoctor().getId().equals(doctor.getId()))
            throw new RuntimeException("Selected queue does not belong to the selected doctor.");
        if (queue.getStatus() != PatientQueue.QueueStatus.ACTIVE) throw new RuntimeException("Queue is not active");
        long waiting = apptRepo.countWaitingByQueueId(queue.getId());
        if (waiting >= queue.getMaxCapacity()) throw new RuntimeException("Queue is full");
        Appointment.Priority priority;
        try { priority = Appointment.Priority.valueOf(req.getPriority().toUpperCase()); }
        catch (Exception e) { priority = Appointment.Priority.NORMAL; }
        int token = apptRepo.findMaxLiveTokenByQueueId(queue.getId()) + 1;
        Appointment appt = apptRepo.save(Appointment.builder().user(user).doctor(doctor).queue(queue)
                .appointmentDate(LocalDateTime.now()).tokenNumber(token).priority(priority)
                .status(Appointment.AppointmentStatus.WAITING).paymentRequired(false)
                .paymentStatus(Payment.PaymentStatus.PAID).rescheduleCount(0).build());
        publisher.publishQueueUpdated(queue.getId());
        return ResponseEntity.ok(ApiResponse.ok("Walk-in registered", AppointmentResponse.builder()
                .id(appt.getId()).patientName(user.getName()).doctorName(doctor.getName())
                .doctorSpecialization(doctor.getSpecialization()).tokenNumber(token)
                .status(appt.getStatus().name()).priority(priority.name()).createdAt(appt.getCreatedAt()).build()));
    }
}
