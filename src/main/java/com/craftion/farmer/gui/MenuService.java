package com.craftion.farmer.gui;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.gui.listener.MenuClickListener;
import com.craftion.farmer.gui.listener.MenuDragListener;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import com.craftion.farmer.util.TextUtil;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuService {

    private static final String DEFAULT_TITLE = "<#38BDF8>ᴄʀᴀғᴛɪᴏɴ ᴄɪғᴛᴄɪ";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final MenuActionRegistry actionRegistry;
    private boolean initialized;

    public MenuService(JavaPlugin plugin, ConfigManager configManager, SchedulerAdapter schedulerAdapter, DebugLogger debugLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.schedulerAdapter = schedulerAdapter;
        this.debugLogger = debugLogger;
        this.actionRegistry = new MenuActionRegistry(debugLogger);
        registerDefaultActions();
    }

    public void initialize() {
        if (this.initialized) {
            return;
        }
        this.plugin.getServer().getPluginManager().registerEvents(new MenuClickListener(this), this.plugin);
        this.plugin.getServer().getPluginManager().registerEvents(new MenuDragListener(), this.plugin);
        this.initialized = true;
        this.debugLogger.debug("Menu listeners registered.");
    }

    public void reload() {
        this.debugLogger.debug("Menu service reloaded.");
    }

    public MenuActionRegistry actionRegistry() {
        return this.actionRegistry;
    }

    public boolean open(Player player, String menuId) {
        return open(player, menuId, null);
    }

    public boolean open(Player player, String menuId, String previousMenuId) {
        if (player == null) {
            return false;
        }

        String normalizedMenuId = normalizeMenuId(menuId);
        String normalizedPreviousMenuId = normalizePreviousMenuId(previousMenuId);
        if (normalizedMenuId == null || this.configManager.guiMenu(normalizedMenuId) == null) {
            this.debugLogger.debug("Menu open skipped because layout is not configured: " + menuId);
            return false;
        }

        this.schedulerAdapter.runAtEntity(player, () -> openNow(player, normalizedMenuId, normalizedPreviousMenuId));
        return true;
    }

    public boolean execute(MenuContext context, MenuAction action) {
        return this.actionRegistry.execute(context, action);
    }

    private void registerDefaultActions() {
        this.actionRegistry.register(MenuAction.Type.OPEN, (context, action) -> open(context.player(), action.target(), context.menuId()));
        this.actionRegistry.register(MenuAction.Type.CLOSE, (context, action) -> close(context.player()));
        this.actionRegistry.register(MenuAction.Type.BACK, this::back);
        this.actionRegistry.register(MenuAction.Type.WITHDRAW, this::deferFutureAction);
        this.actionRegistry.register(MenuAction.Type.SELL, this::deferFutureAction);
    }

    private boolean back(MenuContext context, MenuAction action) {
        return context.holder().previousMenuId().map(previous -> open(context.player(), previous, null)).orElseGet(() -> close(context.player()));
    }

    private boolean close(Player player) {
        if (player == null) {
            return false;
        }
        this.schedulerAdapter.runAtEntity(player, player::closeInventory);
        return true;
    }

    private boolean deferFutureAction(MenuContext context, MenuAction action) {
        this.debugLogger.debug("Menu action is reserved for a later package: " + action.type() + ":" + action.target());
        return false;
    }

    private void openNow(Player player, String menuId, String previousMenuId) {
        if (!player.isOnline()) {
            return;
        }

        ConfigurationSection menuSection = this.configManager.guiMenu(menuId);
        if (menuSection == null) {
            this.debugLogger.debug("Menu layout disappeared before open: " + menuId);
            return;
        }

        MenuLayout layout = layout(menuId, previousMenuId, menuSection);
        Inventory inventory = Bukkit.createInventory(layout.holder(), layout.size(), TextUtil.parse(layout.title()));
        layout.holder().bind(inventory);
        layout.items().forEach(inventory::setItem);
        player.openInventory(inventory);
    }

    private MenuLayout layout(String menuId, String previousMenuId, ConfigurationSection menuSection) {
        int rows = clamp(menuSection.getInt("rows", 3), 1, 6);
        int size = rows * 9;
        String title = menuSection.getString("title", DEFAULT_TITLE);
        Map<Integer, MenuAction> actions = new HashMap<>();
        Map<Integer, ItemStack> items = new HashMap<>();

        ConfigurationSection itemSection = menuSection.getConfigurationSection("items");
        if (itemSection != null) {
            for (String slotKey : itemSection.getKeys(false)) {
                int slot = parseSlot(slotKey);
                if (slot < 0 || slot >= size) {
                    continue;
                }

                ConfigurationSection section = itemSection.getConfigurationSection(slotKey);
                if (section == null) {
                    continue;
                }

                item(section).ifPresent(item -> items.put(slot, item));
                this.actionRegistry.parse(section.getString("action")).ifPresent(action -> actions.put(slot, action));
            }
        }

        return new MenuLayout(new MenuHolder(menuId, previousMenuId, actions), size, title, items);
    }

    private Optional<ItemStack> item(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", ""));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }

        return Optional.of(MenuItemBuilder.of(material)
            .amount(section.getInt("amount", 1))
            .displayName(section.getString("name"))
            .lore(section.getStringList("lore"))
            .build());
    }

    private String normalizeMenuId(String menuId) {
        if (!MenuAction.isKnownMenu(menuId)) {
            return null;
        }
        return menuId.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePreviousMenuId(String previousMenuId) {
        return MenuAction.isKnownMenu(previousMenuId) ? previousMenuId.trim().toLowerCase(Locale.ROOT) : null;
    }

    private int parseSlot(String slotKey) {
        try {
            return Integer.parseInt(slotKey);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record MenuLayout(MenuHolder holder, int size, String title, Map<Integer, ItemStack> items) {
    }
}
