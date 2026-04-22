package com.smartedu.dto;

import lombok.Data;

/**
 * Login request payload.
 */
@Data
public class AuthLoginRequestDto {

    private String username;
    private String password;
}
