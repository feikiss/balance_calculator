package com.fly.hsbchomework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "kubernetes.secrets.datasource")
public class KubernetesSecretConfig {
    private String username;
    private String password;
} 