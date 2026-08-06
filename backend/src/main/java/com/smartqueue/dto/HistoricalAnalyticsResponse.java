package com.smartqueue.dto;
import lombok.*;
import java.util.List;
import java.util.Map;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HistoricalAnalyticsResponse {
    private List<DailyStats> dailyStats;
    private List<DoctorPerformance> doctorPerformance;
    private Map<String,Long> weekdayDistribution;
    private Double overallAvgWaitMinutes;
    private Long totalAppointments;
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyStats { private String date; private Long completed; private Long noShows; private Long cancelled; private Double avgWaitMinutes; }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DoctorPerformance { private String doctorName; private Long totalCompleted; private Double avgConsultationMinutes; private Long noShows; }
}
