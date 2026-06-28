package com.craftion.farmer.hook.visual;

import java.util.Locale;

public enum VisualProviderType {
    FANCY_NPCS,
    NONE;

    public static VisualProviderType from(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }

        try {
            return VisualProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
