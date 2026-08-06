package com.smartqueue.service;

import com.smartqueue.dto.*;
import com.smartqueue.entity.Appointment;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AppointmentRepository appointmentRepo;
    private final DoctorRepository doctorRepo;

    @Transactional(readOnly = true)
    public AnalyticsDashboard getDashboard() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();

        long totalCompleted = appointmentRepo.countAllCompletedSince(startOfDay);
        long totalWaiting   = appointmentRepo.countAllWaiting();
        long totalNoShows   = appointmentRepo.countNoShowsSince(startOfDay);
        long totalBookings  = appointmentRepo.countTotalSince(startOfDay);

        // Compute avg wait in Java (portable H2 + MySQL)
        List<Appointment> completed = appointmentRepo.findCompletedWithTimingsSince(startOfDay);
        double avgWait = completed.stream()
                .filter(a -> a.getCreatedAt() != null && a.getActualStartTime() != null)
                .mapToLong(a -> Duration.between(a.getCreatedAt(), a.getActualStartTime()).toMinutes())
                .filter(m -> m >= 0 && m < 300) // sanity: 0-5 hours
                .average()
                .orElse(0.0);
        avgWait = Math.round(avgWait * 10.0) / 10.0;

        // No-show rate
        double noShowRate = totalBookings > 0
                ? Math.round((double) totalNoShows / totalBookings * 1000.0) / 1000.0
                : 0.0;

        // Doctor loads
        List<DoctorLoad> loads = doctorRepo.findAll().stream().map(d -> {
            List<Appointment> waiting = appointmentRepo.findWaitingByDoctorId(d.getId());
            double avgDoctorWait = waiting.size() *
                    (d.getAvgConsultationTime() != null ? d.getAvgConsultationTime() : 15.0);
            return DoctorLoad.builder()
                    .doctorName(d.getName())
                    .waitingCount((long) waiting.size())
                    .avgWaitMinutes(avgDoctorWait)
                    .build();
        }).collect(Collectors.toList());

        String busiest = loads.stream()
                .max(Comparator.comparing(DoctorLoad::getWaitingCount))
                .map(DoctorLoad::getDoctorName).orElse("N/A");

        // Hourly throughput (native query result)
        List<Object[]> rawHourly = appointmentRepo.countCompletedByHourSince(startOfDay);
        Map<Integer, Long> hourMap = new HashMap<>();
        for (Object[] row : rawHourly) {
            int  hour = ((Number) row[0]).intValue();
            long cnt  = ((Number) row[1]).longValue();
            hourMap.put(hour, cnt);
        }
        List<HourlyThroughput> hourlyThroughput = new ArrayList<>();
        int currentHour = LocalDateTime.now().getHour();
        for (int h = 0; h <= currentHour; h++) {
            hourlyThroughput.add(HourlyThroughput.builder()
                    .hour(h).count(hourMap.getOrDefault(h, 0L)).build());
        }

        // Priority breakdown
        List<Object[]> rawPriority = appointmentRepo.countByPrioritySince(startOfDay);
        Map<String, Long> priorityBreakdown = new LinkedHashMap<>();
        for (Appointment.Priority p : Appointment.Priority.values()) priorityBreakdown.put(p.name(), 0L);
        for (Object[] row : rawPriority) {
            String key = ((Appointment.Priority) row[0]).name();
            long   val = ((Number) row[1]).longValue();
            priorityBreakdown.put(key, val);
        }

        return AnalyticsDashboard.builder()
                .avgWaitingTimeToday(avgWait)
                .noShowRateToday(noShowRate)
                .busiestDoctor(busiest)
                .totalCompletedToday(totalCompleted)
                .totalWaitingNow(totalWaiting)
                .totalNoShowsToday(totalNoShows)
                .totalBookingsToday(totalBookings)
                .hourlyThroughput(hourlyThroughput)
                .doctorLoads(loads)
                .priorityBreakdown(priorityBreakdown)
                .build();
    }
}
