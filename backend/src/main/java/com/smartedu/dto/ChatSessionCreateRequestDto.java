package com.smartedu.dto;

import lombok.Data;

/**
 * Chat session create request payload.
 */
@Data
public class ChatSessionCreateRequestDto {

    private Long userId;
    private String title;
    private String aiModel;
}
