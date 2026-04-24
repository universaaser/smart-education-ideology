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
 * 思政知识实体
 *
 * <p>
 * 固定字典表，只存预定义的高度浓缩思政元素。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("ideology_knowledge")
public class IdeologyKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String keywords;

    private Integer sortOrder;

    private Double positionX;

    private Double positionY;

    private String nodeSize;

    private String icon;

    private String tag;

    private String subTitle;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // 数据库已配置 ON UPDATE CURRENT_TIMESTAMP，不需要应用层 fill
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
