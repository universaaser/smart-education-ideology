package com.smartedu.dto;

import com.smartedu.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {

    private ChatMessage message;

    private List<ChatCitationDto> citations;

    private String retrievalStatus;
}
