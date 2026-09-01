package com.smartqueue.controller;

import com.smartqueue.dto.*;
import com.smartqueue.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctors")
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    @Operation(summary = "List all available doctors with queue info")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> listDoctors() {
        return ResponseEntity.ok(ApiResponse.ok(doctorService.getAllDoctors()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated doctor's profile")
    public ResponseEntity<ApiResponse<DoctorResponse>> getMyDoctor(
            @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) throw new RuntimeException("Not authenticated");
        return ResponseEntity.ok(ApiResponse.ok(doctorService.getMyDoctor(principal.getUsername())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get doctor details")
    public ResponseEntity<ApiResponse<DoctorResponse>> getDoctor(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(doctorService.getDoctorById(id)));
    }
}
