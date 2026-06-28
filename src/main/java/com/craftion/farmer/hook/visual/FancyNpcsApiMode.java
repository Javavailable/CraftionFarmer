package com.craftion.farmer.hook.visual;

import java.util.Locale;

public enum FancyNpcsApiMode {
    AUTO,
    MODERN,
    LEGACY;

    public static FancyNpcsApiMode from(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }

        try {
            return FancyNpcsApiMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return AUTO;
        }
    }
}
