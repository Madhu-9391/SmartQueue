package com.smartqueue.service;

import com.smartqueue.dto.*;
import com.smartqueue.entity.Appointment;
import com.smartqueue.repository.AppointmentRepository;
import com.smartqueue.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AppointmentRepository appointmentRepo;
    private final DoctorRepository doctorRepo;

    @Transactional(readOnly = true)
    public AnalyticsDashboard getDashboard() {

        LocalDateTime startOfDay =
                LocalDateTime.now().toLocalDate().atStartOfDay();

        // Keep the existing business metrics unchanged.
        long totalCompleted =
                appointmentRepo.countAllCompletedSince(startOfDay);

        long totalWaiting =
                appointmentRepo.countAllWaiting();

        long totalNoShows =
                appointmentRepo.countNoShowsSince(startOfDay);

        long totalBookings =
                appointmentRepo.countTotalSince(startOfDay);

        /*
         * OPTIMIZATION #1
         *
         * Previously:
         *   SELECT full Appointment entities
         *   then calculate average in Java.
         *
         * Now:
         *   fetch only createdAt + actualStartTime.
         *
         * Business calculation remains identical.
         */
        List<Object[]> completedTimings =
                appointmentRepo.findCompletedTimingsSince(startOfDay);

        double avgWait = completedTimings.stream()
                .filter(row ->
                        row[0] != null &&
                        row[1] != null
                )
                .mapToLong(row ->
                        Duration.between(
                                (LocalDateTime) row[0],
                                (LocalDateTime) row[1]
                        ).toMinutes()
                )
                .filter(minutes ->
                        minutes >= 0 && minutes < 300
                )
                .average()
                .orElse(0.0);

        avgWait =
                Math.round(avgWait * 10.0) / 10.0;

        // Preserve existing no-show calculation.
        double noShowRate =
                totalBookings > 0
                        ? Math.round(
                                (double) totalNoShows
                                        / totalBookings
                                        * 1000.0
                        ) / 1000.0
                        : 0.0;

        /*
         * OPTIMIZATION #2
         *
         * Previously:
         *
         * doctorRepo.findAll()
         *      +
         * findWaitingByDoctorId() for EVERY doctor
         *
         * That is N+1.
         *
         * Now one grouped query returns the same information.
         */
        List<Object[]> rawDoctorLoads =
                appointmentRepo.findDoctorWaitingLoads();

        List<DoctorLoad> loads = new ArrayList<>();

        for (Object[] row : rawDoctorLoads) {

            String doctorName =
                    (String) row[0];

            Integer avgConsultationTime =
                    row[1] != null
                            ? ((Number) row[1]).intValue()
                            : 15;

            long waitingCount =
                    ((Number) row[2]).longValue();

            double avgDoctorWait =
                    waitingCount * avgConsultationTime;

            loads.add(
                    DoctorLoad.builder()
                            .doctorName(doctorName)
                            .waitingCount(waitingCount)
                            .avgWaitMinutes(avgDoctorWait)
                            .build()
            );
        }

        String busiest =
                loads.stream()
                        .max(
                                Comparator.comparing(
                                        DoctorLoad::getWaitingCount
                                )
                        )
                        .map(DoctorLoad::getDoctorName)
                        .orElse("N/A");

        /*
         * Keep existing hourly throughput logic.
         */
        List<Object[]> rawHourly =
                appointmentRepo.countCompletedByHourSince(
                        startOfDay
                );

        Map<Integer, Long> hourMap =
                new HashMap<>();

        for (Object[] row : rawHourly) {

            int hour =
                    ((Number) row[0]).intValue();

            long count =
                    ((Number) row[1]).longValue();

            hourMap.put(hour, count);
        }

        List<HourlyThroughput> hourlyThroughput =
                new ArrayList<>();

        int currentHour =
                LocalDateTime.now().getHour();

        for (int h = 0; h <= currentHour; h++) {

            hourlyThroughput.add(
                    HourlyThroughput.builder()
                            .hour(h)
                            .count(
                                    hourMap.getOrDefault(
                                            h,
                                            0L
                                    )
                            )
                            .build()
            );
        }

        /*
         * Keep existing priority breakdown.
         */
        List<Object[]> rawPriority =
                appointmentRepo.countByPrioritySince(
                        startOfDay
                );

        Map<String, Long> priorityBreakdown =
                new LinkedHashMap<>();

        for (Appointment.Priority priority :
                Appointment.Priority.values()) {

            priorityBreakdown.put(
                    priority.name(),
                    0L
            );
        }

        for (Object[] row : rawPriority) {

            String key =
                    ((Appointment.Priority) row[0]).name();

            long value =
                    ((Number) row[1]).longValue();

            priorityBreakdown.put(
                    key,
                    value
            );
        }

        /*
         * Same DTO and same API response structure.
         */
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