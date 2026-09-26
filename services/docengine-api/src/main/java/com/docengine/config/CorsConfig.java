package com.docengine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // Vite normally uses localhost; 127.0.0.1 is also a common
                // browser address for the same local machine.
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "OPTIONS")
                // The browser sends the preflight header names in lowercase.
                // Spring matches these case-insensitively, but accepting all
                // request headers keeps multipart uploads and future API
                // headers from being rejected during preflight.
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
