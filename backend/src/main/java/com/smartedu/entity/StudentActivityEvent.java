package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("student_activity_events")
public class StudentActivityEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long courseId;
    private String eventType;
    private Long knowledgePointId;
    private Integer durationSeconds;
    private String payloadJson;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
