package com.fly.hsbchomework.model;

public enum Currency {
    CNY("人民币"),
    USD("美元"),
    EUR("欧元"),
    GBP("英镑"),
    JPY("日元"),
    HKD("港币");

    private final String description;

    Currency(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
} 