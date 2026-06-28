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
}
