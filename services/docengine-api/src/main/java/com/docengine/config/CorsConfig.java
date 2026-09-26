package com.docengine.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(
            @Value("${DOCENGINE_CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}")
            String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
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
