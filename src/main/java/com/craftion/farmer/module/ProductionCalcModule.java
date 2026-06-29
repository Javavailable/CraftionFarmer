package com.craftion.farmer.module;

import com.craftion.farmer.economy.PriceProvider;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.MaterialKey;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

public final class ProductionCalcModule implements FarmerModule {

    public static final String KEY = "production-calc";
    private static final long WINDOW_MILLIS = Duration.ofMinutes(5L).toMillis();

    private final Map<String, Deque<ProductionSample>> samples = new ConcurrentHashMap<>();

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String iconMaterial() {
        return "CLOCK";
    }

    @Override
    public void shutdown() {
        this.samples.clear();
    }

    public void record(Farmer farmer, MaterialKey materialKey, long amount) {
        if (farmer == null || materialKey == null || amount <= 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        Deque<ProductionSample> farmerSamples = this.samples.computeIfAbsent(farmer.farmerId(), ignored -> new ArrayDeque<>());
        synchronized (farmerSamples) {
            farmerSamples.addLast(new ProductionSample(now, materialKey, amount));
            purge(farmerSamples, now);
        }
    }

    public ProductionEstimate estimate(Farmer farmer, PriceProvider priceProvider) {
        if (farmer == null) {
            return ProductionEstimate.empty();
        }

        Deque<ProductionSample> farmerSamples = this.samples.get(farmer.farmerId());
        if (farmerSamples == null) {
            return ProductionEstimate.empty();
        }

        long now = System.currentTimeMillis();
        long amount = 0L;
        double value = 0.0D;
        synchronized (farmerSamples) {
            purge(farmerSamples, now);
            for (ProductionSample sample : farmerSamples) {
                amount = safeAdd(amount, sample.amount());
                if (priceProvider != null && sample.materialKey() != null) {
                    OptionalDouble priceOpt = priceProvider.price(sample.materialKey());
                    if (priceOpt.isPresent()) {
                        value += priceOpt.getAsDouble() * sample.amount();
                    }
                }
            }
        }

        if (amount <= 0L) {
            return ProductionEstimate.empty();
        }

        long perMinute = Math.round(amount * 60.0D / Duration.ofMillis(WINDOW_MILLIS).toSeconds());
        double valuePerMinute = value * 60.0D / Duration.ofMillis(WINDOW_MILLIS).toSeconds();
        return new ProductionEstimate(
            perMinute, safeMultiply(perMinute, 60L), safeMultiply(perMinute, 1440L),
            valuePerMinute, valuePerMinute * 60.0D, valuePerMinute * 1440.0D
        );
    }

    private void purge(Deque<ProductionSample> farmerSamples, long now) {
        while (!farmerSamples.isEmpty() && now - farmerSamples.peekFirst().createdAtMillis() > WINDOW_MILLIS) {
            farmerSamples.removeFirst();
        }
    }

    private long safeAdd(long current, long amount) {
        if (Long.MAX_VALUE - current < amount) {
            return Long.MAX_VALUE;
        }
        return current + amount;
    }

    private long safeMultiply(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) {
            return 0L;
        }
        if (Long.MAX_VALUE / value < multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private record ProductionSample(long createdAtMillis, MaterialKey materialKey, long amount) {
    }
}
