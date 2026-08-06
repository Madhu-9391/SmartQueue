package com.smartqueue.controller;

import com.smartqueue.dto.*;
import com.smartqueue.repository.PaymentRepository;
import com.smartqueue.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService    paymentService;
    private final PaymentRepository paymentRepo;

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentResponse>> createOrder(
            @RequestBody PaymentOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Order created",
                paymentService.createOrder(req.getAppointmentId())));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentResponse>> verify(
            @Valid @RequestBody PaymentVerifyRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Payment verified",
                paymentService.verifyAndConfirm(req)));
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable Long appointmentId) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.getByAppointment(appointmentId)));
    }

    /** BUG 7 FIX: Admin payment stats endpoint */
    @GetMapping("/admin/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminStats() {
        LocalDateTime today     = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime week      = LocalDateTime.now().minusDays(7);
        LocalDateTime month     = LocalDateTime.now().minusDays(30);

        BigDecimal todayRev   = paymentRepo.sumPaidSince(today);
        BigDecimal weekRev    = paymentRepo.sumPaidSince(week);
        BigDecimal monthRev   = paymentRepo.sumPaidSince(month);

        long todayCount       = paymentRepo.countPaidSince(today);
        long weekCount        = paymentRepo.countPaidSince(week);
        long totalCount       = paymentRepo.count();
        long pendingCount     = paymentRepo.countByStatus(com.smartqueue.entity.Payment.PaymentStatus.PENDING);
        long failedCount      = paymentRepo.countByStatus(com.smartqueue.entity.Payment.PaymentStatus.FAILED);

        BigDecimal avgTxn = weekCount > 0
                ? weekRev.divide(BigDecimal.valueOf(weekCount), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "todayRevenue",    todayRev   != null ? todayRev   : BigDecimal.ZERO,
                "weekRevenue",     weekRev    != null ? weekRev    : BigDecimal.ZERO,
                "monthRevenue",    monthRev   != null ? monthRev   : BigDecimal.ZERO,
                "todayCount",      todayCount,
                "weekCount",       weekCount,
                "totalCount",      totalCount,
                "pendingCount",    pendingCount,
                "failedCount",     failedCount,
                "avgTransactionValue", avgTxn,
                "recentPayments",  paymentRepo.findTop10ByOrderByCreatedAtDesc()
        )));
    }
}
