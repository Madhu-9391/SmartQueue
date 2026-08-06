package com.smartqueue.controller;

import com.smartqueue.dto.*;
import com.smartqueue.service.AnalyticsService;
import com.smartqueue.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final QueueService queueService;

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard analytics — accessible to all authenticated users")
    public ResponseEntity<ApiResponse<AnalyticsDashboard>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getDashboard()));
    }

    @GetMapping("/queues")
    @Operation(summary = "All active queues — accessible to all authenticated users")
    public ResponseEntity<ApiResponse<List<QueueStatusResponse>>> allQueues() {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getAllQueues()));
    }
}
