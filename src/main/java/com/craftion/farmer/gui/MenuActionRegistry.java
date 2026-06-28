package com.craftion.farmer.gui;

import com.craftion.farmer.debug.DebugLogger;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.event.inventory.ClickType;

public final class MenuActionRegistry {

    private final DebugLogger debugLogger;
    private final Map<MenuAction.Type, MenuActionHandler> handlers = new EnumMap<>(MenuAction.Type.class);

    public MenuActionRegistry(DebugLogger debugLogger) {
        this.debugLogger = debugLogger;
    }

    public Optional<MenuAction> parse(String rawAction) {
        return MenuAction.parse(rawAction);
    }

    public Optional<MenuAction> resolve(MenuHolder holder, int slot) {
        return resolve(holder, slot, ClickType.LEFT);
    }

    public Optional<MenuAction> resolve(MenuHolder holder, int slot, ClickType clickType) {
        if (holder == null || slot < 0) {
            return Optional.empty();
        }
        return holder.actionAt(slot, clickType);
    }

    public void register(MenuAction.Type type, MenuActionHandler handler) {
        if (type == null || handler == null) {
            return;
        }
        this.handlers.put(type, handler);
    }

    public boolean execute(MenuContext context, MenuAction action) {
        if (context == null || action == null) {
            return false;
        }

        MenuActionHandler handler = this.handlers.get(action.type());
        if (handler == null) {
            this.debugLogger.debug("Menu action has no handler: " + action.type());
            return false;
        }

        return handler.execute(context, action);
    }

    @FunctionalInterface
    public interface MenuActionHandler {

        boolean execute(MenuContext context, MenuAction action);
    }
}
