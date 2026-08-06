package com.smartqueue.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DoctorLoad {
    private String doctorName;
    private Long waitingCount;
    private Double avgWaitMinutes;
}
