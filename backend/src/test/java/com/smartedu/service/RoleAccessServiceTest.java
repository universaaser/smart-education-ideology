package com.smartedu.service;

import com.smartedu.constant.UserRoleConstants;
import com.smartedu.dto.RoleUiConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAccessServiceTest {

    private final RoleAccessService service = new RoleAccessService();

    @Test
    void studentRoleShouldOpenLearningHome() {
        RoleUiConfig config = service.buildRoleUiConfig(UserRoleConstants.STUDENT);

        assertEquals("STUDENT_HOME", config.getDefaultView());
        assertTrue(config.getAllowedViews().contains("STUDENT_HOME"));
        assertTrue(config.getCapabilities().get("canSubmitLearningActivity"));
    }
}
