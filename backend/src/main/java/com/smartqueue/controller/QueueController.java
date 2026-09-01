package com.smartqueue.controller;

import com.smartqueue.dto.*;
import com.smartqueue.entity.PatientQueue;
import com.smartqueue.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.smartqueue.entity.User;
import com.smartqueue.repository.UserRepository;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
@Tag(name = "Queue Management")
public class QueueController {

    private final QueueService queueService;
    private final UserRepository userRepo;

    @PostMapping("/create")
    @Operation(summary = "Create a new queue")
    public ResponseEntity<ApiResponse<PatientQueue>> create(@Valid @RequestBody QueueCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Queue created", queueService.createQueue(req)));
    }

    @GetMapping("/status/{queueId}")
    @Operation(summary = "Get live queue status with AI ETAs")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> status(@PathVariable Long queueId) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getQueueStatus(queueId)));
    }

    @PutMapping("/{queueId}/next")
    @Operation(summary = "Call next token")
    public ResponseEntity<ApiResponse<AppointmentResponse>> callNext(@PathVariable Long queueId,
            @AuthenticationPrincipal UserDetails principal) {
        if (principal != null) {
            User user = userRepo.findByEmail(principal.getUsername()).orElseThrow(() -> new RuntimeException("User not found"));
            if (user.getRole() == User.Role.DOCTOR) {
                PatientQueue q = queueService.getQueueEntity(queueId);
                if (q.getDoctor() == null || !principal.getUsername().equalsIgnoreCase(q.getDoctor().getLinkedEmail()))
                    throw new RuntimeException("Access denied: you can only operate your own queue.");
            }
        }
        return ResponseEntity.ok(ApiResponse.ok("Next token called", queueService.callNextToken(queueId)));
    }
}
