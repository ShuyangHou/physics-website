package com.physics;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 物理实验网站后端启动类
 * 
 * @author HouShuYang
 */
@SpringBootApplication
@MapperScan("com.physics.mapper")
public class PhysicsWebsiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhysicsWebsiteApplication.class, args);
    }
} 