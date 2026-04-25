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
            case UserRoleConstants.STUDENT -> "STUDENT_HOME";
            case UserRoleConstants.ADMIN -> "DASHBOARD";
            default -> "DASHBOARD";
        };
    }

    private List<String> resolveAllowedViews(String role) {
        return switch (role) {
            case UserRoleConstants.STUDENT -> List.of(
                    "STUDENT_HOME",
                    "KNOWLEDGE_GRAPH",
                    "AI_ASSISTANT",
                    "COURSE_LIBRARY");
            case UserRoleConstants.ADMIN -> List.of(
                    "DASHBOARD",
                    "KNOWLEDGE_GRAPH",
                    "AI_ASSISTANT",
                    "RESOURCE_UPLOAD",
                    "COURSE_LIBRARY",
                    "COURSE_MANAGEMENT",
                    "ALERTS",
                    "SOURCE_MANAGEMENT",
                    "KEYWORD_TASKS",
                    "MATCH_REVIEW",
                    "MODEL_SETTINGS",
                    "ADMIN_CONSOLE");
            default -> List.of(
                    "DASHBOARD",
                    "KNOWLEDGE_GRAPH",
                    "AI_ASSISTANT",
                    "RESOURCE_UPLOAD",
                    "COURSE_LIBRARY",
                    "COURSE_MANAGEMENT",
                    "ALERTS",
                    "SOURCE_MANAGEMENT",
                    "KEYWORD_TASKS",
                    "MATCH_REVIEW");
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
        capabilities.put("canManageSources", isTeacher || isAdmin);
        capabilities.put("canManageKeywordTasks", isTeacher || isAdmin);
        capabilities.put("canReviewMatches", isTeacher || isAdmin);
        capabilities.put("canManageAiProviders", isAdmin);
        capabilities.put("canManageAdminConsole", isAdmin);
        capabilities.put("canSubmitLearningActivity", isStudent);
        return capabilities;
    }
}
