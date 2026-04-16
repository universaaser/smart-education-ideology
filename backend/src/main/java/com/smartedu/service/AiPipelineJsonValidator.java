package com.smartedu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 流水线 JSON 响应校验器。
 *
 * <p>
 * 该组件负责在各阶段将模型输出转换为可控结构，避免自由文本直接进入后续流程。
 */
@Component
public class AiPipelineJsonValidator {

    private final ObjectMapper objectMapper;

    public AiPipelineJsonValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析并校验 JSON 对象，不通过时抛出异常。
     */
    public JsonNode parseObject(String rawJson, List<String> requiredFields, List<String> allowedFields) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("AI response is empty");
        }

        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (!node.isObject()) {
                throw new IllegalArgumentException("AI response is not a JSON object");
            }
            validateFields(node, requiredFields, allowedFields);
            return node;
        } catch (IOException ex) {
            throw new IllegalArgumentException("AI response is not valid JSON", ex);
        }
    }

    /**
     * 校验字符串字段最大长度。
     */
    public void validateStringLength(JsonNode node, String field, int maxLength) {
        JsonNode target = node.get(field);
        if (target != null && target.isTextual() && target.asText().length() > maxLength) {
            throw new IllegalArgumentException("Field \"" + field + "\" exceeds max length: " + maxLength);
        }
    }

    /**
     * 校验数组字段最大数量。
     */
    public void validateArraySize(JsonNode node, String field, int maxSize) {
        JsonNode target = node.get(field);
        if (target != null && target.isArray() && target.size() > maxSize) {
            throw new IllegalArgumentException("Field \"" + field + "\" exceeds max size: " + maxSize);
        }
    }

    private void validateFields(JsonNode node, List<String> requiredFields, List<String> allowedFields) {
        Set<String> allowedSet = new HashSet<>(allowedFields);
        for (String required : requiredFields) {
            if (!node.has(required)) {
                throw new IllegalArgumentException("Missing required field: " + required);
            }
        }

        node.fieldNames().forEachRemaining(field -> {
            if (!allowedSet.contains(field)) {
                throw new IllegalArgumentException("Unexpected field: " + field);
            }
        });
    }
}
