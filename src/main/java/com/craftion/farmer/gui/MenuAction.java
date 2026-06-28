package com.craftion.farmer.gui;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record MenuAction(Type type, String target) {

    private static final Set<String> OPEN_TARGETS = Set.of("main", "storage", "manage", "members", "modules");
    private static final String PRODUCT_MENU_PREFIX = "product:";

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
        if (action.equals("collect-toggle")) {
            return Optional.of(new MenuAction(Type.COLLECT_TOGGLE, ""));
        }
        if (action.startsWith("product-toggle:")) {
            String target = target(action, "product-toggle:");
            return isMaterialTarget(target) ? Optional.of(new MenuAction(Type.PRODUCT_TOGGLE, target)) : Optional.empty();
        }
        if (action.startsWith("open:")) {
            String target = target(action, "open:");
            return isOpenTarget(target) ? Optional.of(new MenuAction(Type.OPEN, target)) : Optional.empty();
        }
        if (action.startsWith("withdraw-dialog:")) {
            String target = target(action, "withdraw-dialog:");
            return isMaterialTarget(target) ? Optional.of(new MenuAction(Type.WITHDRAW_DIALOG, target)) : Optional.empty();
        }
        if (action.startsWith("sell-dialog:")) {
            String target = target(action, "sell-dialog:");
            return isMaterialTarget(target) ? Optional.of(new MenuAction(Type.SELL_DIALOG, target)) : Optional.empty();
        }
        if (action.startsWith("withdraw:")) {
            String target = target(action, "withdraw:");
            return isWithdrawTarget(target) ? Optional.of(new MenuAction(Type.WITHDRAW, target)) : Optional.empty();
        }
        if (action.startsWith("sell:")) {
            String target = target(action, "sell:");
            return isSellTarget(target) ? Optional.of(new MenuAction(Type.SELL, target)) : Optional.empty();
        }
        if (action.startsWith("module-toggle:")) {
            String target = target(action, "module-toggle:");
            return isMaterialTarget(target) ? Optional.of(new MenuAction(Type.MODULE_TOGGLE, target)) : Optional.empty();
        }

        return Optional.empty();
    }

    public boolean opensMenu() {
        return this.type == Type.OPEN;
    }

    public static boolean isKnownMenu(String menuId) {
        return menuId != null && isOpenTarget(menuId.trim().toLowerCase(Locale.ROOT));
    }

    private static String target(String action, String prefix) {
        return action.substring(prefix.length()).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isOpenTarget(String target) {
        return OPEN_TARGETS.contains(target) || isProductMenuTarget(target);
    }

    private static boolean isProductMenuTarget(String target) {
        if (target == null || !target.startsWith(PRODUCT_MENU_PREFIX)) {
            return false;
        }
        return isStorageMaterialTarget(target.substring(PRODUCT_MENU_PREFIX.length()));
    }

    private static boolean isValidTarget(Type type, String target) {
        return switch (type) {
            case OPEN -> isOpenTarget(target);
            case CLOSE, BACK, INFO, COLLECT_TOGGLE -> target.isEmpty();
            case WITHDRAW -> isWithdrawTarget(target);
            case SELL -> isSellTarget(target);
            case WITHDRAW_DIALOG, SELL_DIALOG, PRODUCT_TOGGLE -> isMaterialTarget(target);
            case MODULE_TOGGLE -> isMaterialTarget(target);
        };
    }

    private static boolean isWithdrawTarget(String target) {
        String[] parts = parts(target);
        if (parts.length == 1) {
            return isStorageMaterialTarget(parts[0]);
        }
        return parts.length == 2 && isStorageMaterialTarget(parts[0]) && (parts[1].equals("stack") || parts[1].equals("all"));
    }

    private static boolean isSellTarget(String target) {
        if ("all".equals(target)) {
            return true;
        }
        String[] parts = parts(target);
        if (parts.length == 1) {
            return isStorageMaterialTarget(parts[0]);
        }
        return parts.length == 2 && isStorageMaterialTarget(parts[0]) && parts[1].equals("all");
    }

    private static String[] parts(String target) {
        return target == null ? new String[0] : target.split(":", -1);
    }

    private static boolean isStorageMaterialTarget(String target) {
        return isMaterialTarget(target) && !target.equals("all");
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
        WITHDRAW_DIALOG,
        SELL,
        SELL_DIALOG,
        MODULE_TOGGLE,
        COLLECT_TOGGLE,
        PRODUCT_TOGGLE
    }
}
