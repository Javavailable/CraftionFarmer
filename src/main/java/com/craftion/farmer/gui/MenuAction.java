package com.craftion.farmer.gui;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record MenuAction(Type type, String target) {

    private static final Set<String> OPEN_TARGETS = Set.of("main", "storage", "manage", "members", "modules");

    public MenuAction {
        if (type == null) {
            throw new IllegalArgumentException("Menu action type cannot be null.");
        }
        target = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (!isValidTarget(type, target)) {
            throw new IllegalArgumentException("Unsupported menu action target: " + target);
        }
    }

    public static Optional<MenuAction> parse(String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            return Optional.empty();
        }

        String action = rawAction.trim().toLowerCase(Locale.ROOT);
        if (action.equals("close")) {
            return Optional.of(new MenuAction(Type.CLOSE, ""));
        }
        if (action.equals("back")) {
            return Optional.of(new MenuAction(Type.BACK, ""));
        }
        if (action.equals("info")) {
            return Optional.of(new MenuAction(Type.INFO, ""));
        }
        if (action.startsWith("open:")) {
            String target = target(action, "open:");
            return OPEN_TARGETS.contains(target) ? Optional.of(new MenuAction(Type.OPEN, target)) : Optional.empty();
        }
        if (action.startsWith("withdraw:")) {
            String target = target(action, "withdraw:");
            return isMaterialTarget(target) ? Optional.of(new MenuAction(Type.WITHDRAW, target)) : Optional.empty();
        }
        if (action.startsWith("sell:")) {
            String target = target(action, "sell:");
            return isMaterialTarget(target) ? Optional.of(new MenuAction(Type.SELL, target)) : Optional.empty();
        }

        return Optional.empty();
    }

    public boolean opensMenu() {
        return this.type == Type.OPEN;
    }

    public static boolean isKnownMenu(String menuId) {
        return menuId != null && OPEN_TARGETS.contains(menuId.trim().toLowerCase(Locale.ROOT));
    }

    private static String target(String action, String prefix) {
        return action.substring(prefix.length()).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidTarget(Type type, String target) {
        return switch (type) {
            case OPEN -> OPEN_TARGETS.contains(target);
            case CLOSE, BACK, INFO -> target.isEmpty();
            case WITHDRAW, SELL -> isMaterialTarget(target);
        };
    }

    private static boolean isMaterialTarget(String target) {
        if (target == null || target.isBlank() || target.length() > 64) {
            return false;
        }

        for (int index = 0; index < target.length(); index++) {
            char character = target.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '_' && character != '-') {
                return false;
            }
        }
        return true;
    }

    public enum Type {
        OPEN,
        CLOSE,
        BACK,
        INFO,
        WITHDRAW,
        SELL
    }
}
