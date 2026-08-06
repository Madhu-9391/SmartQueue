package com.smartqueue.dto;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PredictionResult {
    private LocalDateTime predictedVisitTime;
    private Integer confidenceMinutes;
    private Integer estimatedWaitMinutes;
    private Double modelConfidenceScore;
    private String predictionBasis;
}
