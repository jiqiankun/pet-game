package com.petgame.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 * <p>
 * 确保 SPA 前端路由正常工作：所有非 API、非静态资源的请求转发到 index.html。
 * 由于前端使用 Hash 模式路由（/#/path），此处仅需保证根路径返回 index.html。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径转发到 index.html（由 Spring Boot 静态资源提供）
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
