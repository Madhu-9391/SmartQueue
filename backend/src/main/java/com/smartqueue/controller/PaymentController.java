package com.smartqueue.controller;

import com.smartqueue.dto.*;
import com.smartqueue.repository.PaymentRepository;
import com.smartqueue.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService    paymentService;
    private final PaymentRepository paymentRepo;

    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentResponse>> createOrder(
            @RequestBody PaymentOrderRequest req, @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Order created",
                paymentService.createOrder(req.getAppointmentId(), principal.getUsername())));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentResponse>> verify(
            @Valid @RequestBody PaymentVerifyRequest req, @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Payment verified",
                paymentService.verifyAndConfirm(req, principal.getUsername())));
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable Long appointmentId, @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.getByAppointmentForUser(appointmentId, principal.getUsername())));
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

        List<Map<String, Object>> recent = paymentRepo.findTop10ByOrderByCreatedAtDesc().stream().map(p -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("appointmentId", p.getAppointment() != null ? p.getAppointment().getId() : null);
            item.put("amount", p.getAmount());
            item.put("currency", p.getCurrency());
            item.put("status", p.getStatus() != null ? p.getStatus().name() : null);
            item.put("razorpayPaymentId", p.getRazorpayPaymentId());
            item.put("paidAt", p.getPaidAt());
            item.put("createdAt", p.getCreatedAt());
            return item;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayRevenue", todayRev != null ? todayRev : BigDecimal.ZERO);
        result.put("weekRevenue", weekRev != null ? weekRev : BigDecimal.ZERO);
        result.put("monthRevenue", monthRev != null ? monthRev : BigDecimal.ZERO);
        result.put("todayCount", todayCount);
        result.put("weekCount", weekCount);
        result.put("totalCount", totalCount);
        result.put("pendingCount", pendingCount);
        result.put("failedCount", failedCount);
        result.put("avgTransactionValue", avgTxn);
        result.put("recentPayments", recent);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
