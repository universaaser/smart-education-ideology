package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Bootstrap payload used by future role-specific frontend shells.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthBootstrapResponse {

    private Map<String, Object> user;

    private RoleUiConfig roleUi;
}
