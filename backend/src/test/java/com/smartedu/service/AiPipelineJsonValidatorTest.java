package com.smartedu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiPipelineJsonValidatorTest {

    private AiPipelineJsonValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiPipelineJsonValidator(new ObjectMapper());
    }

    @Test
    void shouldParseValidJsonObject() {
        String raw = """
                {
                  "title": "Network Fundamentals",
                  "documentType": "TEXTBOOK",
                  "overview": "Overview text",
                  "chapterOutline": ["Chapter 1"],
                  "teachingFocus": ["Focus 1"]
                }
                """;
        JsonNode node = validator.parseObject(
                raw,
                List.of("title", "documentType", "overview", "chapterOutline", "teachingFocus"),
                List.of("title", "documentType", "overview", "chapterOutline", "teachingFocus"));

        assertDoesNotThrow(() -> validator.validateStringLength(node, "overview", 200));
        assertDoesNotThrow(() -> validator.validateArraySize(node, "chapterOutline", 5));
    }

    @Test
    void shouldFailWhenMissingRequiredField() {
        String raw = """
                {
                  "title": "Only Title"
                }
                """;
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.parseObject(raw, List.of("title", "overview"), List.of("title", "overview")));
    }

    @Test
    void shouldFailWhenUnexpectedFieldExists() {
        String raw = """
                {
                  "title": "Doc",
                  "overview": "Overview",
                  "extra": "Not allowed"
                }
                """;
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.parseObject(raw, List.of("title", "overview"), List.of("title", "overview")));
    }

    @Test
    void shouldFailWhenResponseIsNotJson() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.parseObject("not-json", List.of("a"), List.of("a")));
    }

    @Test
    void shouldFailWhenArrayExceedsLimit() {
        String raw = """
                {
                  "items": ["a", "b", "c"]
                }
                """;
        JsonNode node = validator.parseObject(raw, List.of("items"), List.of("items"));
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateArraySize(node, "items", 2));
    }
}
