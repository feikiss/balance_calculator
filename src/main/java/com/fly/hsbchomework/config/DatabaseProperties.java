package com.fly.hsbchomework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.datasource")
public class DatabaseProperties {
    private String url;
    private String username;
    private String password;
    private String driverClassName;
} 