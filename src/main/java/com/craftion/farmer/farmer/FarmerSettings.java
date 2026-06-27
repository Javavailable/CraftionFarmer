package com.craftion.farmer.farmer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FarmerSettings {

    private final ConcurrentMap<String, String> values = new ConcurrentHashMap<>();

    public FarmerSettings() {
    }

    public FarmerSettings(Map<String, String> values) {
        FarmerValidation.requireNonNull(values, "values").forEach(this::set);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(this.values.get(normalizeKey(key)));
    }

    public void set(String key, String value) {
        this.values.put(normalizeKey(key), FarmerValidation.requireNonNull(value, "value"));
    }

    public void remove(String key) {
        this.values.remove(normalizeKey(key));
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(this.values);
    }

    public FarmerSettings copy() {
        return new FarmerSettings(snapshot());
    }

    private String normalizeKey(String key) {
        return FarmerValidation.requireNonBlank(key, "settingKey");
    }
}
