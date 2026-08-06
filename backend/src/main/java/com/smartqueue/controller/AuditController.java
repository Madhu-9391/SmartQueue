package com.smartqueue.controller;
import com.smartqueue.dto.*;
import com.smartqueue.service.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/admin/audit") @RequiredArgsConstructor @Tag(name="Audit")
public class AuditController {
    private final PriorityAuditService auditService;
    private final HistoricalAnalyticsService historicalService;
    @GetMapping("/priority")
    public ResponseEntity<ApiResponse<List<PriorityAuditResponse>>> getAuditLog(@RequestParam(defaultValue="7") int days) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getRecentAuditLog(days)));
    }
    @GetMapping("/priority/appointment/{apptId}")
    public ResponseEntity<ApiResponse<List<PriorityAuditResponse>>> getForAppointment(@PathVariable Long apptId) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.getForAppointment(apptId)));
    }
    @GetMapping("/historical")
    public ResponseEntity<ApiResponse<HistoricalAnalyticsResponse>> getHistorical(@RequestParam(defaultValue="7") int days) {
        return ResponseEntity.ok(ApiResponse.ok(historicalService.getHistory(days)));
    }
}
