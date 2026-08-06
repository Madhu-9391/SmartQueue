package com.smartqueue.repository;

import com.smartqueue.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByAppointmentId(Long appointmentId);
    Optional<Payment> findByRazorpayOrderId(String orderId);
    long countByStatus(Payment.PaymentStatus status);
    List<Payment> findTop10ByOrderByCreatedAtDesc();

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status='PAID' AND p.paidAt>=:since")
    BigDecimal sumPaidSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status='PAID' AND p.paidAt>=:since")
    Long countPaidSince(@Param("since") LocalDateTime since);
}
