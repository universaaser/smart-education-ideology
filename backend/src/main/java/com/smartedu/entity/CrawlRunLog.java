package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("crawl_run_logs")
public class CrawlRunLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sourceId;

    private String sourceName;

    private String status;

    private Integer totalFetched;

    private Integer totalCreated;

    private Integer totalDeduplicated;

    private Integer totalFailed;

    private String errorSummary;

    private String statsJson;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
