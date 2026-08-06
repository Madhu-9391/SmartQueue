package com.smartqueue.service;
import com.smartqueue.dto.PriorityAuditResponse;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Slf4j
public class PriorityAuditService {
    private final PriorityAuditLogRepository auditRepo;
    private final UserRepository userRepo;

    @Transactional
    public void log(Appointment appointment, Appointment.Priority prev, Appointment.Priority next, String changedByEmail, String reason) {
        User changedBy = changedByEmail != null ? userRepo.findByEmail(changedByEmail).orElse(null) : null;
        auditRepo.save(PriorityAuditLog.builder().appointment(appointment).changedBy(changedBy).previousPriority(prev).newPriority(next).reason(reason).build());
        log.info("Priority audit: appt={} {}→{} by {}", appointment.getId(), prev, next, changedByEmail);
    }

    public List<PriorityAuditResponse> getRecentAuditLog(int days) {
        return auditRepo.findAllSince(LocalDateTime.now().minusDays(days)).stream().map(this::toResponse).collect(Collectors.toList());
    }
    public List<PriorityAuditResponse> getForAppointment(Long apptId) {
        return auditRepo.findByAppointmentIdOrderByChangedAtDesc(apptId).stream().map(this::toResponse).collect(Collectors.toList());
    }
    public long countEmergencyEscalationsToday() {
        return auditRepo.countEmergencyEscalationsSince(LocalDateTime.now().toLocalDate().atStartOfDay());
    }
    private PriorityAuditResponse toResponse(PriorityAuditLog l) {
        return PriorityAuditResponse.builder().id(l.getId()).appointmentId(l.getAppointment().getId()).tokenNumber(l.getAppointment().getTokenNumber()).patientName(l.getAppointment().getUser().getName()).changedByName(l.getChangedBy()!=null?l.getChangedBy().getName():"System").previousPriority(l.getPreviousPriority()!=null?l.getPreviousPriority().name():"—").newPriority(l.getNewPriority().name()).reason(l.getReason()).changedAt(l.getChangedAt()).build();
    }
}
