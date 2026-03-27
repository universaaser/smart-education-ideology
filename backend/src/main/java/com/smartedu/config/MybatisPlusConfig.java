package com.smartedu.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * 
 * <p>
 * 配置 MyBatis-Plus 的各种插件，包括分页插件等。
 * 
 * @author SmartEducation Team
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 插件
     * 
     * <p>
     * 主要配置分页插件，支持 MySQL 数据库的分页查询。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件配置
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 设置最大单页限制数量，默认 500 条，-1 不受限制
        paginationInterceptor.setMaxLimit(500L);
        // 为 true 时，如果 pageSize > maxLimit，则替换为 maxLimit
        paginationInterceptor.setOverflow(true);

        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}
