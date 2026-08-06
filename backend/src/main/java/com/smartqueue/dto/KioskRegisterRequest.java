package com.smartqueue.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class KioskRegisterRequest { @NotBlank private String name; @NotBlank private String phone; @NotBlank private String priority; @NotNull private Long doctorId; @NotNull private Long queueId; }
