package com.craftion.farmer.hook.region;

import java.util.Locale;

public enum RegionProviderType {
    SKYLLIA,
    NONE;

    public static RegionProviderType from(String value) {
        if (value == null || value.isBlank()) {
            return SKYLLIA;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return SKYLLIA;
        }
    }
}
