package com.smartqueue.dto;
import lombok.*;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AnalyticsDashboard {
    private Double avgWaitingTimeToday;
    private Double noShowRateToday;
    private String busiestDoctor;
    private Long totalCompletedToday;
    private Long totalWaitingNow;
    private Long totalNoShowsToday;
    private Long totalBookingsToday;
    private List<HourlyThroughput> hourlyThroughput;
    private List<DoctorLoad> doctorLoads;
    private Map<String, Long> priorityBreakdown;
}
