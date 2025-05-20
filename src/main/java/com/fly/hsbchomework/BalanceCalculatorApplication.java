package com.fly.hsbchomework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableCaching
@EnableTransactionManagement
@EnableRetry
public class BalanceCalculatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(BalanceCalculatorApplication.class, args);
    }
} 