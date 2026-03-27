package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Role-aware frontend bootstrap configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleUiConfig {

    private String role;

    private String roleLabel;

    private String defaultView;

    private List<String> allowedViews;

    private Map<String, Boolean> capabilities;
}
