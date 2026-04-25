package com.smartedu.controller;

import com.smartedu.common.Result;
import com.smartedu.dto.AiProviderConfigDto;
import com.smartedu.dto.AiProviderConfigRequestDto;
import com.smartedu.dto.AiProviderTestResultDto;
import com.smartedu.dto.AiRouteConfigDto;
import com.smartedu.dto.AiRouteConfigRequestDto;
import com.smartedu.service.AiProviderConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-providers")
@RequiredArgsConstructor
public class AiProviderConfigController {

    private final AiProviderConfigService aiProviderConfigService;

    @GetMapping
    public Result<List<AiProviderConfigDto>> listProviders() {
        return Result.success(aiProviderConfigService.listProviders());
    }

    @PutMapping("/{providerKey}")
    public Result<AiProviderConfigDto> saveProvider(
            @PathVariable String providerKey,
            @RequestBody AiProviderConfigRequestDto request) {
        if (!aiProviderConfigService.supportsProvider(providerKey)) {
            return Result.badRequest("Unsupported AI provider");
        }
        AiProviderConfigDto provider = aiProviderConfigService.saveProvider(providerKey, request);
        if (provider == null) {
            return Result.badRequest("Invalid AI provider config");
        }
        return Result.success(provider);
    }

    @PostMapping("/{providerKey}/test")
    public Result<AiProviderTestResultDto> testProvider(@PathVariable String providerKey) {
        if (!aiProviderConfigService.supportsProvider(providerKey)) {
            return Result.badRequest("Unsupported AI provider");
        }
        return Result.success(aiProviderConfigService.testProvider(providerKey));
    }

    @GetMapping("/routes")
    public Result<List<AiRouteConfigDto>> listRoutes() {
        return Result.success(aiProviderConfigService.listRoutes());
    }

    @PutMapping("/routes/{taskType}")
    public Result<AiRouteConfigDto> saveRoute(
            @PathVariable String taskType,
            @RequestBody AiRouteConfigRequestDto request) {
        if (!aiProviderConfigService.supportsTask(taskType)) {
            return Result.badRequest("Unsupported AI route task");
        }
        AiRouteConfigDto route = aiProviderConfigService.saveRoute(taskType, request);
        if (route == null) {
            return Result.badRequest("Invalid AI route config");
        }
        return Result.success(route);
    }
}
