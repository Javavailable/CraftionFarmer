package com.craftion.farmer.module;

public record ProductionEstimate(long perMinute, long perHour, long perDay) {

    public ProductionEstimate {
        perMinute = Math.max(0L, perMinute);
        perHour = Math.max(0L, perHour);
        perDay = Math.max(0L, perDay);
    }

    public static ProductionEstimate empty() {
        return new ProductionEstimate(0L, 0L, 0L);
    }
}
