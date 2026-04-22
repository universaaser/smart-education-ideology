package com.smartedu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Placeholder payload for the legacy manual-crawl trigger endpoint.
 *
 * <p>
 * The current implementation ignores request-body fields, but keeping a DTO here avoids
 * controller-level ad-hoc map parsing while preserving compatibility with older callers that
 * still send extra JSON fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceManualCrawlRequestDto {
}
