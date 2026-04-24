package com.smartedu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 
 * <p>
 * 对应数据库表：users
 * <p>
 * 存储教师、学生、管理员等用户信息
 * 
 * @author SmartEducation Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {

    /**
     * 用户ID（主键自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名/教工号
     */
    private String username;

    /**
     * 密码（BCrypt加密存储）
     */
    private String password;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 角色类型
     * TEACHER: 教师
     * STUDENT: 学生
     * ADMIN: 管理员
     */
    private String role;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 院系/专业
     */
    private String department;

    /**
     * 状态：0-禁用 1-正常
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    // 数据库已配置 ON UPDATE CURRENT_TIMESTAMP，不需要应用层 fill
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记：0-未删除 1-已删除
     */
    @TableLogic
    private Integer deleted;
}
