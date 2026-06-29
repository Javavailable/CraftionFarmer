package com.craftion.farmer.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private TextUtil() {
    }

    public static Component parse(String message) {
        return MINI_MESSAGE.deserialize(message == null ? "" : message)
            .decoration(TextDecoration.ITALIC, false);
    }

    public static String regionDisplayName(String ownerName, String regionId) {
        if (ownerName == null || ownerName.isBlank() || isUuid(ownerName)) {
            return regionId != null ? regionId : "Ada";
        }
        return ownerName + "'ın Adası";
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
