package com.smartqueue.service;
import com.smartqueue.dto.*;
import com.smartqueue.entity.Appointment;
import com.smartqueue.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class HistoricalAnalyticsService {
    private final AppointmentRepository apptRepo;
    private final DoctorRepository doctorRepo;

    @Cacheable(value="historicalAnalytics",key="#days")
    @Transactional(readOnly=true)
    public HistoricalAnalyticsResponse getHistory(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd");

        List<HistoricalAnalyticsResponse.DailyStats> dailyStats = new ArrayList<>();
        for (int d = days-1; d >= 0; d--) {
            LocalDateTime dayStart = LocalDateTime.now().minusDays(d).toLocalDate().atStartOfDay();
            LocalDateTime dayEnd   = dayStart.plusDays(1);
            long completed  = apptRepo.findAllFiltered(Appointment.AppointmentStatus.COMPLETED,null).stream().filter(a->a.getActualEndTime()!=null&&!a.getActualEndTime().isBefore(dayStart)&&a.getActualEndTime().isBefore(dayEnd)).count();
            long noShows    = apptRepo.findAllFiltered(Appointment.AppointmentStatus.NO_SHOW,null).stream().filter(a->a.getCreatedAt()!=null&&!a.getCreatedAt().isBefore(dayStart)&&a.getCreatedAt().isBefore(dayEnd)).count();
            long cancelled  = apptRepo.findAllFiltered(Appointment.AppointmentStatus.CANCELLED,null).stream().filter(a->a.getCreatedAt()!=null&&!a.getCreatedAt().isBefore(dayStart)&&a.getCreatedAt().isBefore(dayEnd)).count();
            List<Appointment> done = apptRepo.findCompletedWithTimingsSince(dayStart).stream().filter(a->a.getActualStartTime()!=null&&a.getActualStartTime().isBefore(dayEnd)).collect(Collectors.toList());
            double avgWait = done.stream().filter(a->a.getCreatedAt()!=null).mapToLong(a->Duration.between(a.getCreatedAt(),a.getActualStartTime()).toMinutes()).filter(m->m>=0&&m<300).average().orElse(0.0);
            dailyStats.add(HistoricalAnalyticsResponse.DailyStats.builder().date(dayStart.format(fmt)).completed(completed).noShows(noShows).cancelled(cancelled).avgWaitMinutes(Math.round(avgWait*10.0)/10.0).build());
        }

        List<HistoricalAnalyticsResponse.DoctorPerformance> doctorPerf = doctorRepo.findAll().stream().map(d->{
            long comp = apptRepo.countCompletedSince(d.getId(),since);
            long ns   = apptRepo.countNoShowsByDoctorSince(d.getId(),since);
            List<Appointment> done = apptRepo.findCompletedWithTimingsSince(since).stream().filter(a->a.getDoctor().getId().equals(d.getId())&&a.getActualEndTime()!=null&&a.getActualStartTime()!=null).collect(Collectors.toList());
            double avg = done.stream().mapToLong(a->Duration.between(a.getActualStartTime(),a.getActualEndTime()).toMinutes()).filter(m->m>0&&m<120).average().orElse(0.0);
            return HistoricalAnalyticsResponse.DoctorPerformance.builder().doctorName(d.getName()).totalCompleted(comp).avgConsultationMinutes(Math.round(avg*10.0)/10.0).noShows(ns).build();
        }).filter(dp->dp.getTotalCompleted()>0||dp.getNoShows()>0).collect(Collectors.toList());

        Map<String,Long> weekdayMap = new LinkedHashMap<>();
        for (String day : new String[]{"MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"}) weekdayMap.put(day,0L);
        apptRepo.findCompletedWithTimingsSince(since).forEach(a->{ if(a.getCreatedAt()!=null) weekdayMap.merge(a.getCreatedAt().getDayOfWeek().name(),1L,Long::sum); });

        List<Appointment> all = apptRepo.findCompletedWithTimingsSince(since);
        double overallAvg = all.stream().filter(a->a.getCreatedAt()!=null&&a.getActualStartTime()!=null).mapToLong(a->Duration.between(a.getCreatedAt(),a.getActualStartTime()).toMinutes()).filter(m->m>=0&&m<300).average().orElse(0.0);

        return HistoricalAnalyticsResponse.builder().dailyStats(dailyStats).doctorPerformance(doctorPerf).weekdayDistribution(weekdayMap).overallAvgWaitMinutes(Math.round(overallAvg*10.0)/10.0).totalAppointments(apptRepo.countTotalSince(since)).build();
    }
}
