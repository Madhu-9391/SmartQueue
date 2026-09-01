package com.smartqueue.service;

import com.smartqueue.dto.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.websocket.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Slf4j
public class DoctorPortalService {

    private final DoctorRepository doctorRepo;
    private final AppointmentRepository apptRepo;
    private final AiPredictionService predictionService;
    private final SocketEventPublisher publisher;
    private final NotificationDispatchService notifDispatch;
    private final QueueService queueService;

    /**
     * BUG 4 FIX: Match doctor entity to logged-in user by name.
     * In production, add a userId FK on Doctor entity.
     */
    public boolean isOwnDoctor(Long doctorId, String userEmail) {
        return doctorRepo.findByLinkedEmail(userEmail)
                .map(d -> d.getId().equals(doctorId))
                .orElse(false);
    }

    public List<AppointmentResponse> getMyQueue(Long doctorId) {
        return apptRepo.findActiveQueueForDoctor(doctorId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public DoctorStatsResponse getMyStats(Long doctorId) {
        Doctor doctor = doctorRepo.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();

        long completed   = apptRepo.countCompletedByDoctorSince(doctorId, startOfDay);
        long waiting     = apptRepo.findWaitingByDoctorId(doctorId).size();
        long noShows     = apptRepo.countNoShowsByDoctorSince(doctorId, startOfDay);
        long emergencies = apptRepo.countEmergenciesByDoctorSince(doctorId, startOfDay);

        List<Appointment> done = apptRepo.findCompletedWithTimingsSince(startOfDay).stream()
                .filter(a -> a.getDoctor().getId().equals(doctorId)
                        && a.getActualStartTime() != null && a.getActualEndTime() != null)
                .collect(Collectors.toList());
        double avgConsult = done.stream()
                .mapToLong(a -> Duration.between(a.getActualStartTime(), a.getActualEndTime()).toMinutes())
                .filter(m -> m > 0 && m < 120)
                .average().orElse(0.0);

        List<Object[]> raw = apptRepo.countCompletedByHourSince(startOfDay);
        Map<Integer,Long> hourMap = new HashMap<>();
        for (Object[] row : raw) hourMap.put(((Number)row[0]).intValue(), ((Number)row[1]).longValue());
        List<HourlyThroughput> hourly = new ArrayList<>();
        for (int h = 0; h <= LocalDateTime.now().getHour(); h++)
            hourly.add(HourlyThroughput.builder().hour(h).count(hourMap.getOrDefault(h,0L)).build());

        return DoctorStatsResponse.builder()
                .doctorId(doctorId).doctorName(doctor.getName())
                .completedToday(completed).waitingNow(waiting)
                .avgConsultationMinutesToday(Math.round(avgConsult * 10.0) / 10.0)
                .noShowsToday(noShows).emergenciesToday(emergencies)
                .hourlyThroughput(hourly).build();
    }

    @Transactional
    public AppointmentResponse callNext(Long doctorId) {
        if (!doctorRepo.existsById(doctorId)) throw new RuntimeException("Doctor not found");
        PatientQueue queue = queueService.getActiveQueueEntityByDoctorId(doctorId);
        return queueService.callNextToken(queue.getId());
    }

    @Transactional
    public AppointmentResponse markDone(Long doctorId, Long appointmentId) {
        Appointment appt = apptRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        // BUG 4 FIX: strict ownership check
        if (!appt.getDoctor().getId().equals(doctorId))
            throw new RuntimeException("Access denied: this appointment belongs to another doctor.");
        if (appt.getStatus() != Appointment.AppointmentStatus.ACTIVE)
            throw new RuntimeException("Only the active appointment can be marked done.");
        if (appt.getActualStartTime() == null)
            appt.setActualStartTime(LocalDateTime.now());
        appt.setStatus(Appointment.AppointmentStatus.COMPLETED);
        appt.setActualEndTime(LocalDateTime.now());
        apptRepo.save(appt);
        if (appt.getQueue() != null) {
            List<Appointment> updated = predictionService.recalculateQueuePredictions(appt.getQueue().getId());
            apptRepo.saveAll(updated);
            publisher.publishQueueUpdated(appt.getQueue().getId());
            publisher.publishEtaUpdated(appt.getQueue().getId());
            updated.stream().filter(a -> a.getStatus() == Appointment.AppointmentStatus.WAITING)
                    .findFirst().ifPresent(next -> {
                        if (next.getPredictedVisitTime() != null)
                            notifDispatch.dispatchEtaUpdated(next.getUser().getId(),
                                    next.getPredictedVisitTime().toLocalTime().toString(),
                                    next.getPredictionConfidence() != null ? next.getPredictionConfidence() : 10);
                    });
        }
        return toResponse(appt);
    }

    @Transactional
    public void markNoShow(Long doctorId, Long appointmentId) {
        Appointment appt = apptRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        if (!appt.getDoctor().getId().equals(doctorId))
            throw new RuntimeException("Access denied: this appointment belongs to another doctor.");
        appt.setStatus(Appointment.AppointmentStatus.NO_SHOW);
        apptRepo.save(appt);
        if (appt.getQueue() != null) {
            predictionService.recalculateQueuePredictions(appt.getQueue().getId());
            publisher.publishQueueUpdated(appt.getQueue().getId());
        }
    }

    @Transactional
    public DoctorResponse updateMyAvailability(Long doctorId, String status) {
        Doctor doctor = doctorRepo.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        doctor.setAvailabilityStatus(Doctor.AvailabilityStatus.valueOf(status.toUpperCase()));
        doctorRepo.save(doctor);
        long qs = apptRepo.findWaitingByDoctorId(doctorId).size();
        return DoctorResponse.builder()
                .id(doctor.getId()).name(doctor.getName()).specialization(doctor.getSpecialization())
                .avgConsultationTime(doctor.getAvgConsultationTime())
                .availabilityStatus(doctor.getAvailabilityStatus().name())
                .roomNumber(doctor.getRoomNumber()).delayMinutes(doctor.getDelayMinutes())
                .currentQueueSize((int)qs).build();
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId()).patientName(a.getUser().getName())
                .doctorName(a.getDoctor().getName()).tokenNumber(a.getTokenNumber())
                .status(a.getStatus().name()).priority(a.getPriority().name())
                .predictedVisitTime(a.getPredictedVisitTime())
                .predictionConfidence(a.getPredictionConfidence())
                .createdAt(a.getCreatedAt()).paymentRequired(a.getPaymentRequired()).paymentStatus(a.getPaymentStatus() != null ? a.getPaymentStatus().name() : null).build();
    }
}
