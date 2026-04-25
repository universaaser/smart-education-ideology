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
@TableName("ai_route_configs")
public class AiRouteConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskType;

    private String providerKey;

    private String model;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
