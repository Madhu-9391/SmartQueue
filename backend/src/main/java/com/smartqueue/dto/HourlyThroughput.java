package com.smartqueue.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HourlyThroughput {
    private Integer hour;
    private Long count;
}
