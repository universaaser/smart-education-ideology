package com.smartedu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAlertSummaryDto {

    private long total;
    private long pending;
    private long processing;
    private long resolved;
    private long ignored;
    private long mild;
    private long moderate;
    private long severe;
}
