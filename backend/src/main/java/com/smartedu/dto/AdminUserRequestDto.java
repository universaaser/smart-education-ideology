package com.smartedu.dto;

import lombok.Data;

@Data
public class AdminUserRequestDto {
    private String username;
    private String password;
    private String email;
    private String realName;
    private String role;
    private String department;
}
