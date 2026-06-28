package com.physics.config;

import com.physics.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Bean
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor())
                .addPathPatterns("/**")
                // 注意：/files/** 不再排除鉴权，上传文件需登录后方可访问，
                // 避免上传内容被匿名用户猜名下载。
                .excludePathPatterns("/api/auth/login");
    }

    /**
     * 配置跨域请求（CORS）设置。
     * 该方法允许来自任何来源的客户端进行跨域请求，并允许指定的请求方法和请求头。
     * 它还配置了跨域请求的最大时效为 1 小时（3600秒），并支持凭证（cookie）传输。
     *
     * @param registry 用于注册 CORS 配置的注册器。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")  // 配置跨域规则，允许所有路径
                .allowedOrigins("http://localhost:3000") // 允许的具体来源域名
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")  // 允许的方法
                .allowCredentials(true)  // 允许发送凭证（如cookie）
                .maxAge(3600)  // 设置预检请求的缓存时间（单位秒）
                .allowedHeaders("*");  // 允许所有请求头
    }

    @Value("${file.upload.path:uploads}")
    private String uploadRoot;

    // 静态资源映射：/files/** -> {uploadRoot}
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadRoot;
        if (!location.endsWith("/")) location = location + "/";
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + location);
    }
} 