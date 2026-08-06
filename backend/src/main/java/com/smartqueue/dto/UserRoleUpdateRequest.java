package com.smartqueue.dto;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class UserRoleUpdateRequest {
    @NotNull private Long userId;
    @NotBlank private String role;
}
