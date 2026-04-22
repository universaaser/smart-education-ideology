package com.smartedu.dto;

import lombok.Data;

/**
 * Register request payload.
 */
@Data
public class AuthRegisterRequestDto {

    private String username;
    private String password;
    private String email;
    private String role;
}
