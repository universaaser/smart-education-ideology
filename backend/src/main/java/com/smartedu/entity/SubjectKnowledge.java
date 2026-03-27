package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学科知识实体
 *
 * <p>
 * 运行时主表，承接学科知识节点和后续人工新增学科知识。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("subject_knowledge")
public class SubjectKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String subject;

    private String category;

    private String summary;

    private String ideologySummary;

    private String sourceUrl;

    private Double positionX;

    private Double positionY;

    private String nodeSize;

    private String icon;

    private String tag;

    private String subTitle;

    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
