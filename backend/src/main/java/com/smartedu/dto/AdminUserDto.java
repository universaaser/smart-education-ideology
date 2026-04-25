package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDto {
    private Long id;
    private String username;
    private String email;
    private String realName;
    private String role;
    private String department;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
