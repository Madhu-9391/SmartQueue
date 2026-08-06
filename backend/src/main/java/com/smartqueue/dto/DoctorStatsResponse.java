package com.smartqueue.dto;
import lombok.*;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DoctorStatsResponse { private Long doctorId; private String doctorName; private Long completedToday; private Long waitingNow; private Double avgConsultationMinutesToday; private Long noShowsToday; private Long emergenciesToday; private List<HourlyThroughput> hourlyThroughput; }
