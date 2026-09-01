package com.smartqueue.controller;

import com.smartqueue.dto.*;
import com.smartqueue.entity.User;
import com.smartqueue.repository.UserRepository;
import com.smartqueue.service.DoctorPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doctor-portal")
@RequiredArgsConstructor
@Tag(name = "Doctor Portal")
public class DoctorPortalController {

    private final DoctorPortalService portalService;
    private final UserRepository userRepo;

    /**
     * BUG 4 FIX: Doctors can only see THEIR OWN queue.
     * Admin can see any doctor's queue by passing doctorId.
     */
    @GetMapping("/my-queue/{doctorId}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getMyQueue(
            @PathVariable Long doctorId,
            @AuthenticationPrincipal UserDetails principal) {
        verifyDoctorAccess(doctorId, principal);
        return ResponseEntity.ok(ApiResponse.ok(portalService.getMyQueue(doctorId)));
    }

    @GetMapping("/stats/{doctorId}")
    public ResponseEntity<ApiResponse<DoctorStatsResponse>> getStats(
            @PathVariable Long doctorId,
            @AuthenticationPrincipal UserDetails principal) {
        verifyDoctorAccess(doctorId, principal);
        return ResponseEntity.ok(ApiResponse.ok(portalService.getMyStats(doctorId)));
    }

    /**
     * BUG 4 FIX: Doctor can only mark DONE their own appointments.
     */
    @PutMapping("/{doctorId}/next")
    public ResponseEntity<ApiResponse<AppointmentResponse>> callNext(
            @PathVariable Long doctorId,
            @AuthenticationPrincipal UserDetails principal) {
        verifyDoctorAccess(doctorId, principal);
        return ResponseEntity.ok(ApiResponse.ok("Next patient called",
                portalService.callNext(doctorId)));
    }

    @PutMapping("/{doctorId}/appointments/{appointmentId}/done")
    public ResponseEntity<ApiResponse<AppointmentResponse>> markDone(
            @PathVariable Long doctorId,
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal UserDetails principal) {
        verifyDoctorAccess(doctorId, principal);
        return ResponseEntity.ok(ApiResponse.ok("Marked done",
                portalService.markDone(doctorId, appointmentId)));
    }

    @PutMapping("/{doctorId}/appointments/{appointmentId}/no-show")
    public ResponseEntity<ApiResponse<Void>> markNoShow(
            @PathVariable Long doctorId,
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal UserDetails principal) {
        verifyDoctorAccess(doctorId, principal);
        portalService.markNoShow(doctorId, appointmentId);
        return ResponseEntity.ok(ApiResponse.ok("Marked no-show", null));
    }

    @PutMapping("/{doctorId}/availability")
    public ResponseEntity<ApiResponse<DoctorResponse>> updateAvailability(
            @PathVariable Long doctorId,
            @RequestParam String status,
            @AuthenticationPrincipal UserDetails principal) {
        verifyDoctorAccess(doctorId, principal);
        return ResponseEntity.ok(ApiResponse.ok("Updated",
                portalService.updateMyAvailability(doctorId, status)));
    }

    /**
     * BUG 4 FIX: For DOCTOR role — verify the doctorId matches their profile.
     * ADMIN can access any doctor's data.
     */
    private void verifyDoctorAccess(Long doctorId, UserDetails principal) {
        if (principal == null) throw new RuntimeException("Not authenticated");
        User user = userRepo.findByEmail(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() == User.Role.ADMIN) return; // admin can access any

        // For DOCTOR: their doctorId must match
        if (user.getRole() == User.Role.DOCTOR) {
            if (!portalService.isOwnDoctor(doctorId, user.getEmail())) {
                throw new RuntimeException("Access denied: you can only view your own queue.");
            }
        } else {
            throw new RuntimeException("Access denied: doctor portal is for DOCTOR and ADMIN roles only.");
        }
    }
}
