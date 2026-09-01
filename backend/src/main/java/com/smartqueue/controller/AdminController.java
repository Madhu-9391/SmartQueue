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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Operations")
public class AdminController {

    private final QueueService            queueService;
    private final AnalyticsService        analyticsService;
    private final DoctorService           doctorService;
    private final UserManagementService   userManagementService;
    private final PriorityAuditService    auditService;
    private final AppointmentRepository   apptRepo;
    private final PatientQueueRepository  queueRepo;
    private final DoctorRepository        doctorRepo;
    private final UserRepository          userRepo;
    private final SocketEventPublisher    publisher;
    private final AiPredictionService     predictionService;
    private final NotificationDispatchService notifDispatch;
    private final PasswordEncoder         passwordEncoder;

    // ─── ANALYTICS ────────────────────────────────────────────
    @GetMapping("/analytics/dashboard")
    public ResponseEntity<ApiResponse<AnalyticsDashboard>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getDashboard()));
    }

    // ─── DOCTOR MANAGEMENT ────────────────────────────────────
    @GetMapping("/doctors")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> listDoctors() {
        return ResponseEntity.ok(ApiResponse.ok(doctorService.getAllDoctors()));
    }

    @PostMapping("/doctors")
    @Operation(summary = "Admin: create doctor + auto-create queue + optional login account")
    public ResponseEntity<ApiResponse<DoctorResponse>> createDoctor(
            @Valid @RequestBody DoctorCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Doctor created with queue", doctorService.createDoctor(req)));
    }

    @PutMapping("/doctors/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> updateDoctor(
            @PathVariable Long id, @RequestBody DoctorUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Doctor updated", doctorService.updateDoctor(id, req)));
    }

    @DeleteMapping("/doctors/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDoctor(@PathVariable Long id) {
        doctorService.deleteDoctor(id);
        return ResponseEntity.ok(ApiResponse.ok("Doctor deleted", null));
    }

    @PutMapping("/doctors/{id}/availability")
    public ResponseEntity<ApiResponse<DoctorResponse>> updateAvailability(
            @PathVariable Long id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.ok("Updated", doctorService.updateAvailability(id, status)));
    }

    @PutMapping("/doctors/delay")
    public ResponseEntity<ApiResponse<String>> updateDoctorDelay(
            @Valid @RequestBody DoctorDelayRequest req) {
        doctorRepo.findById(req.getDoctorId()).ifPresent(doctor -> {
            doctor.setDelayMinutes(req.getDelayMinutes());
            doctorRepo.save(doctor);
            queueRepo.findActiveQueueByDoctorId(doctor.getId()).ifPresent(q -> {
                List<Appointment> updated = predictionService.recalculateQueuePredictions(q.getId());
                apptRepo.saveAll(updated);
                publisher.publishEtaUpdated(q.getId());
                updated.forEach(a -> notifDispatch.dispatchDoctorDelayed(
                        a.getUser().getId(), doctor.getName(), req.getDelayMinutes(),
                        a.getPredictedVisitTime() != null ? a.getPredictedVisitTime().toLocalTime().toString() : "TBD"));
            });
            publisher.publishDoctorDelayed(doctor.getId(), req.getDelayMinutes(), req.getReason());
        });
        return ResponseEntity.ok(ApiResponse.ok("Delay updated. Patients notified.", null));
    }

    // ─── USER MANAGEMENT ──────────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userManagementService.getAllUsers()));
    }

    @GetMapping("/users/role/{role}")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listByRole(@PathVariable String role) {
        return ResponseEntity.ok(ApiResponse.ok(userManagementService.getUsersByRole(role)));
    }

    /**
     * BUG 1+10 FIX: Only ADMIN can assign DOCTOR or ADMIN roles.
     * Adding a DOCTOR role user creates a login account for them.
     */
    @PostMapping("/users/create-staff")
    @Operation(summary = "Admin: create DOCTOR or ADMIN account (admin only)")
    public ResponseEntity<ApiResponse<UserResponse>> createStaffAccount(
            @RequestBody Map<String, String> body) {
        String name  = body.get("name");
        String email = body.get("email");
        String password = body.get("password");
        String role  = body.getOrDefault("role", "DOCTOR");
        String phone = body.get("phone");

        if (!List.of("DOCTOR", "ADMIN").contains(role.toUpperCase()))
            throw new RuntimeException("Only DOCTOR or ADMIN roles can be created here");
        if (name == null || name.isBlank() || email == null || email.isBlank())
            throw new RuntimeException("Name and email are required.");
        if (password == null || password.length() < 12)
            throw new RuntimeException("Staff password must be at least 12 characters.");

        if (userRepo.existsByEmail(email))
            throw new IllegalArgumentException("Email already registered: " + email);

        User user = User.builder()
                .name(name).email(email)
                .password(passwordEncoder.encode(password))
                .phone(phone)
                .role(User.Role.valueOf(role.toUpperCase()))
                .build();
        userRepo.save(user);

        return ResponseEntity.ok(ApiResponse.ok("Staff account created",
                UserResponse.builder().id(user.getId()).name(user.getName())
                        .email(user.getEmail()).role(user.getRole().name()).phone(user.getPhone()).build()));
    }

    @PutMapping("/users/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateRole(
            @Valid @RequestBody UserRoleUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Role updated", userManagementService.updateRole(req)));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userManagementService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User deleted", null));
    }

    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        userManagementService.resetPassword(id, body.get("newPassword"));
        return ResponseEntity.ok(ApiResponse.ok("Password reset", null));
    }

    // ─── QUEUE MANAGEMENT ─────────────────────────────────────
    @GetMapping("/queues")
    public ResponseEntity<ApiResponse<List<QueueStatusResponse>>> listQueues() {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getAllQueues()));
    }

    @PutMapping("/queues/{id}")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> updateQueue(
            @PathVariable Long id, @RequestBody QueueUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Queue updated", queueService.updateQueue(id, req)));
    }

    @DeleteMapping("/queues/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQueue(@PathVariable Long id) {
        queueService.deleteQueue(id);
        return ResponseEntity.ok(ApiResponse.ok("Queue deleted", null));
    }

    @PostMapping("/queues/{id}/reset")
    public ResponseEntity<ApiResponse<Void>> resetQueue(@PathVariable Long id) {
        queueService.resetQueue(id);
        return ResponseEntity.ok(ApiResponse.ok("Queue reset", null));
    }

    // ─── APPOINTMENT MANAGEMENT ───────────────────────────────
    @GetMapping("/appointments")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> listAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long doctorId) {
        Appointment.AppointmentStatus s = null;
        if (status != null) {
            try { s = Appointment.AppointmentStatus.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        List<AppointmentResponse> result = apptRepo.findAllFiltered(s, doctorId).stream().map(a -> AppointmentResponse.builder()
                .id(a.getId()).patientName(a.getUser().getName()).doctorName(a.getDoctor().getName())
                .doctorSpecialization(a.getDoctor().getSpecialization()).tokenNumber(a.getTokenNumber())
                .status(a.getStatus().name()).priority(a.getPriority().name()).appointmentDate(a.getAppointmentDate())
                .createdAt(a.getCreatedAt()).predictedVisitTime(a.getPredictedVisitTime())
                .predictionConfidence(a.getPredictionConfidence()).lastPredictionUpdated(a.getLastPredictionUpdated()).build())
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping("/appointments/{id}/priority")
    public ResponseEntity<ApiResponse<String>> updatePriority(
            @PathVariable Long id, @RequestParam String priority,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails principal) {
        apptRepo.findById(id).ifPresent(appt -> {
            Appointment.Priority prev = appt.getPriority();
            Appointment.Priority next = Appointment.Priority.valueOf(priority.toUpperCase());
            appt.setPriority(next);
            apptRepo.save(appt);
            auditService.log(appt, prev, next, principal != null ? principal.getUsername() : null, reason);
            List<Appointment> updated = predictionService.recalculateQueuePredictions(appt.getQueue().getId());
            apptRepo.saveAll(updated);
            publisher.publishEtaUpdated(appt.getQueue().getId());
        });
        return ResponseEntity.ok(ApiResponse.ok("Priority updated and audited", null));
    }

    @PutMapping("/appointments/{id}/complete")
    public ResponseEntity<ApiResponse<String>> complete(@PathVariable Long id) {
        apptRepo.findById(id).ifPresent(appt -> {
            appt.setStatus(Appointment.AppointmentStatus.COMPLETED);
            appt.setActualEndTime(LocalDateTime.now());
            apptRepo.save(appt);
            List<Appointment> updated = predictionService.recalculateQueuePredictions(appt.getQueue().getId());
            apptRepo.saveAll(updated);
            publisher.publishQueueUpdated(appt.getQueue().getId());
        });
        return ResponseEntity.ok(ApiResponse.ok("Completed", null));
    }

    @PutMapping("/appointments/{id}/cancel")
    public ResponseEntity<ApiResponse<String>> cancelAppointment(
            @PathVariable Long id,
            @RequestBody(required = false) CancelAppointmentRequest req) {
        apptRepo.findById(id).ifPresent(appt -> {
            appt.setStatus(Appointment.AppointmentStatus.CANCELLED);
            appt.setCancellationType(Appointment.CancellationType.ADMIN_CANCELLED);
            appt.setCancellationReason(req != null ? req.getReason() : null);
            apptRepo.save(appt);
            List<Appointment> updated = predictionService.recalculateQueuePredictions(appt.getQueue().getId());
            apptRepo.saveAll(updated);
            publisher.publishQueueUpdated(appt.getQueue().getId());
            notifDispatch.dispatchCancelled(appt.getUser().getId(), appt.getTokenNumber(), req != null ? req.getReason() : null);
        });
        return ResponseEntity.ok(ApiResponse.ok("Cancelled and patient notified", null));
    }

    @PutMapping("/appointments/{id}/no-show")
    public ResponseEntity<ApiResponse<String>> markNoShow(@PathVariable Long id) {
        apptRepo.findById(id).ifPresent(appt -> {
            appt.setStatus(Appointment.AppointmentStatus.NO_SHOW);
            appt.setCancellationType(Appointment.CancellationType.NO_SHOW_AUTO);
            apptRepo.save(appt);
            List<Appointment> updated = predictionService.recalculateQueuePredictions(appt.getQueue().getId());
            apptRepo.saveAll(updated);
            publisher.publishQueueUpdated(appt.getQueue().getId());
        });
        return ResponseEntity.ok(ApiResponse.ok("Marked no-show", null));
    }

    @PostMapping("/notify/broadcast")
    public ResponseEntity<ApiResponse<String>> broadcast(
            @RequestParam Long queueId, @RequestParam String message) {
        apptRepo.findWaitingAndActiveByQueueId(queueId).forEach(a ->
                notifDispatch.dispatch(a.getUser().getId(), message, Notification.NotificationType.GENERAL));
        publisher.publishQueueUpdated(queueId);
        return ResponseEntity.ok(ApiResponse.ok("Broadcast sent", null));
    }


}
