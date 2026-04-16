package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Material version summary item for version list.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialVersionItemDto {

    private Long materialId;

    private Integer versionNo;

    private String status;

    private Integer isLatest;

    private LocalDateTime updatedAt;
}
