package com.smartedu;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Backend application entry point.
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.smartedu.mapper")
@EnableAsync
public class SmartEducationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartEducationApplication.class, args);
        log.info("============================================");
        log.info("Smart Education backend started");
        log.info("Access URL: http://localhost:8080");
        log.info("============================================");
    }
}
