package com.smartedu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智教思政后端服务启动类
 * 
 * <p>功能说明：
 * <ul>
 *   <li>启用 Spring Boot 自动配置</li>
 *   <li>启用 MyBatis-Plus Mapper 扫描</li>
 *   <li>启用异步任务支持（用于 AI 分析等耗时操作）</li>
 * </ul>
 * 
 * @author SmartEducation Team
 * @version 1.0.0
 */
@SpringBootApplication
@MapperScan("com.smartedu.mapper")
@EnableAsync
public class SmartEducationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartEducationApplication.class, args);
        System.out.println("============================================");
        System.out.println("   智教思政后端服务启动成功！");
        System.out.println("   访问地址：http://localhost:8080");
        System.out.println("============================================");
    }
}
