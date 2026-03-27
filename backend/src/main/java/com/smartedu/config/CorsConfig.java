package com.smartedu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置类
 * 
 * <p>
 * 配置 CORS（跨源资源共享），允许前端应用跨域访问后端 API。
 * 
 * NOTE: 生产环境应该限制允许的源地址，不要使用 "*"。
 * 
 * @author SmartEducation Team
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许所有源（开发环境）
        // FIXME: 生产环境应该修改为具体的前端域名
        config.addAllowedOriginPattern("*");

        // 允许发送 Cookie
        config.setAllowCredentials(true);

        // 允许所有请求头
        config.addAllowedHeader("*");

        // 允许所有请求方法
        config.addAllowedMethod("*");

        // 暴露响应头（前端可读取）
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Content-Disposition");

        // 预检请求缓存时间（秒）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
