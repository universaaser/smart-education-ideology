package com.smartedu.service;

import com.smartedu.constant.UserRoleConstants;
import com.smartedu.dto.RoleUiConfig;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a stable role-to-view contract for the frontend.
 */
@Service
public class RoleAccessService {

    public RoleUiConfig buildRoleUiConfig(String role) {
        String normalizedRole = UserRoleConstants.normalize(role);
        return new RoleUiConfig(
                normalizedRole,
                UserRoleConstants.getRoleLabel(normalizedRole),
                resolveDefaultView(normalizedRole),
                resolveAllowedViews(normalizedRole),
                resolveCapabilities(normalizedRole));
    }

    private String resolveDefaultView(String role) {
        return switch (role) {
            case UserRoleConstants.STUDENT -> "KNOWLEDGE_GRAPH";
            case UserRoleConstants.ADMIN -> "DASHBOARD";
            default -> "DASHBOARD";
        };
    }

    private List<String> resolveAllowedViews(String role) {
        return switch (role) {
            case UserRoleConstants.STUDENT -> List.of(
                    "KNOWLEDGE_GRAPH",
                    "AI_ASSISTANT",
                    "COURSE_LIBRARY");
            case UserRoleConstants.ADMIN -> List.of(
                    "DASHBOARD",
                    "KNOWLEDGE_GRAPH",
                    "AI_ASSISTANT",
                    "RESOURCE_UPLOAD",
                    "COURSE_LIBRARY");
            default -> List.of(
                    "DASHBOARD",
                    "KNOWLEDGE_GRAPH",
                    "AI_ASSISTANT",
                    "RESOURCE_UPLOAD",
                    "COURSE_LIBRARY");
        };
    }

    private Map<String, Boolean> resolveCapabilities(String role) {
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        boolean isTeacher = UserRoleConstants.TEACHER.equals(role);
        boolean isStudent = UserRoleConstants.STUDENT.equals(role);
        boolean isAdmin = UserRoleConstants.ADMIN.equals(role);

        capabilities.put("canViewDashboard", isTeacher || isAdmin);
        capabilities.put("canUseAiAssistant", true);
        capabilities.put("canViewCourseLibrary", true);
        capabilities.put("canViewKnowledgeGraph", true);
        capabilities.put("canEditKnowledgeGraph", isTeacher || isAdmin);
        capabilities.put("canUploadResource", isTeacher || isAdmin);
        capabilities.put("canTriggerCrawlUpdate", isTeacher || isAdmin);
        capabilities.put("canCreateCourse", isTeacher || isAdmin);
        capabilities.put("canManageUsers", isAdmin);
        capabilities.put("canViewStudentAlerts", isTeacher || isAdmin);
        capabilities.put("canSubmitLearningActivity", isStudent);
        return capabilities;
    }
}
