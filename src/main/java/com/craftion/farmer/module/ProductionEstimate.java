package com.craftion.farmer.module;

public record ProductionEstimate(long perMinute, long perHour, long perDay, double valuePerMinute, double valuePerHour, double valuePerDay) {

    public ProductionEstimate {
        perMinute = Math.max(0L, perMinute);
        perHour = Math.max(0L, perHour);
        perDay = Math.max(0L, perDay);

        if (Double.isNaN(valuePerMinute) || Double.isInfinite(valuePerMinute) || valuePerMinute < 0.0D) {
            valuePerMinute = 0.0D;
        }
        if (Double.isNaN(valuePerHour) || Double.isInfinite(valuePerHour) || valuePerHour < 0.0D) {
            valuePerHour = 0.0D;
        }
        if (Double.isNaN(valuePerDay) || Double.isInfinite(valuePerDay) || valuePerDay < 0.0D) {
            valuePerDay = 0.0D;
        }
    }

    public static ProductionEstimate empty() {
        return new ProductionEstimate(0L, 0L, 0L, 0.0D, 0.0D, 0.0D);
    }
}
