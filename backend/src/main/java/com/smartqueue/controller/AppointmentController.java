package com.smartqueue.controller;

import com.smartqueue.dto.*;
import com.smartqueue.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping("/book")
    @Operation(summary = "Book appointment — returns AI-predicted visit time")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            @Valid @RequestBody AppointmentRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok("Appointment booked",
                appointmentService.bookAppointment(req, user.getUsername())));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user appointments")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> myAppointments(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(
                appointmentService.getMyAppointments(user.getUsername())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel appointment")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long id,
            @RequestBody AppointmentCancelRequest request,
            @AuthenticationPrincipal UserDetails user) {
        appointmentService.cancelAppointment(id, user.getUsername(),request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Appointment cancelled", null));
    }
}
