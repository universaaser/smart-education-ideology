package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRetrievalResult {

    private String retrievalStatus;

    private List<KnowledgeContextItem> contexts;
}
