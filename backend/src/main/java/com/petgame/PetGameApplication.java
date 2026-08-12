package com.petgame;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 宠物精灵游戏主启动类。
 * <p>
 * 个人开发、个人部署、个人游玩的轻量单机 Web 宠物收集养成游戏。
 * 前端构建产物打入 Spring Boot 静态资源，输出单个可执行 JAR。
 */
@SpringBootApplication
@MapperScan("com.petgame.**.mapper")
public class PetGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetGameApplication.class, args);
    }
}
