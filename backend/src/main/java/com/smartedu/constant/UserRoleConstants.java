package com.smartedu.constant;

/**
 * Centralized user role constants and helpers.
 */
public final class UserRoleConstants {

    public static final String TEACHER = "TEACHER";
    public static final String STUDENT = "STUDENT";
    public static final String ADMIN = "ADMIN";

    private UserRoleConstants() {
    }

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return TEACHER;
        }

        String normalized = role.trim().toUpperCase();
        if (TEACHER.equals(normalized) || STUDENT.equals(normalized) || ADMIN.equals(normalized)) {
            return normalized;
        }
        return TEACHER;
    }

    public static String getRoleLabel(String role) {
        String normalized = normalize(role);
        return switch (normalized) {
            case STUDENT -> "Student";
            case ADMIN -> "Administrator";
            default -> "Teacher";
        };
    }
}
