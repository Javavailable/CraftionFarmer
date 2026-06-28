package com.craftion.farmer.gui;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import com.craftion.farmer.farmer.FarmerRole;
import com.craftion.farmer.gui.listener.MenuClickListener;
import com.craftion.farmer.gui.listener.MenuDragListener;
import com.craftion.farmer.hook.region.RegionAccessResult;
import com.craftion.farmer.hook.region.RegionMemberInfo;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.hook.skyllia.FarmerReconcileService;
import com.craftion.farmer.message.MessageService;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import com.craftion.farmer.storage.DatabaseManager;
import com.craftion.farmer.util.TextUtil;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuService {

    private static final String USE_PERMISSION = "craftionfarmer.use";
    private static final String DEFAULT_TITLE = "<#38BDF8>ᴄʀᴀғᴛɪᴏɴ ᴄɪғᴛᴄɪ";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final DatabaseManager databaseManager;
    private final RegionProviderManager regionProviderManager;
    private final FarmerPersistenceService farmerPersistenceService;
    private final FarmerReconcileService farmerReconcileService;
    private final MessageService messageService;
    private final MenuActionRegistry actionRegistry;
    private final Map<String, FarmerMenu> menus = new LinkedHashMap<>();
    private boolean initialized;

    public MenuService(
        JavaPlugin plugin,
        ConfigManager configManager,
        SchedulerAdapter schedulerAdapter,
        DebugLogger debugLogger,
        DatabaseManager databaseManager,
        RegionProviderManager regionProviderManager,
        FarmerPersistenceService farmerPersistenceService,
        FarmerReconcileService farmerReconcileService,
        MessageService messageService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.schedulerAdapter = schedulerAdapter;
        this.debugLogger = debugLogger;
        this.databaseManager = databaseManager;
        this.regionProviderManager = regionProviderManager;
        this.farmerPersistenceService = farmerPersistenceService;
        this.farmerReconcileService = farmerReconcileService;
        this.messageService = messageService;
        this.actionRegistry = new MenuActionRegistry(debugLogger);
        registerMenus();
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

    public boolean openMain(Player player) {
        return openForPlayer(player, "main", null);
    }

    public boolean open(Player player, String menuId) {
        return openForPlayer(player, menuId, null);
    }

    public boolean open(Player player, String menuId, String previousMenuId) {
        return openForPlayer(player, menuId, previousMenuId);
    }

    public boolean execute(MenuContext context, MenuAction action) {
        return this.actionRegistry.execute(context, action);
    }

    private void registerMenus() {
        registerMenu(new MainFarmerMenu());
        registerMenu(new StorageMenu());
        registerMenu(new ManageMenu());
        registerMenu(new MembersMenu());
        registerMenu(new ModulesMenu());
    }

    private void registerMenu(FarmerMenu menu) {
        this.menus.put(menu.id(), menu);
    }

    private void registerDefaultActions() {
        this.actionRegistry.register(MenuAction.Type.OPEN, (context, action) -> context.session()
            .map(session -> openResolved(context.player(), action.target(), context.menuId(), session))
            .orElseGet(() -> openForPlayer(context.player(), action.target(), context.menuId())));
        this.actionRegistry.register(MenuAction.Type.CLOSE, (context, action) -> close(context.player()));
        this.actionRegistry.register(MenuAction.Type.BACK, this::back);
        this.actionRegistry.register(MenuAction.Type.INFO, (context, action) -> true);
        this.actionRegistry.register(MenuAction.Type.WITHDRAW, this::deferFutureAction);
        this.actionRegistry.register(MenuAction.Type.SELL, this::deferFutureAction);
    }

    private boolean openForPlayer(Player player, String menuId, String previousMenuId) {
        if (player == null) {
            return false;
        }

        String normalizedMenuId = normalizeMenuId(menuId);
        if (normalizedMenuId == null) {
            this.debugLogger.debug("Menu open skipped because menu id is unsupported: " + menuId);
            return false;
        }

        String normalizedPreviousMenuId = normalizePreviousMenuId(previousMenuId);
        this.schedulerAdapter.runAtEntity(player, () -> resolveAndOpen(player, normalizedMenuId, normalizedPreviousMenuId));
        return true;
    }

    private void resolveAndOpen(Player player, String menuId, String previousMenuId) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            this.messageService.send(player, "commands.farmer.no-permission");
            return;
        }

        RegionProvider provider = this.regionProviderManager.provider();
        if (provider == null || !provider.isAvailable()) {
            this.messageService.send(player, "commands.farmer.provider-unavailable");
            return;
        }

        RegionAccessResult access = provider.access(player.getLocation(), player.getUniqueId());
        if (!access.allowed()) {
            sendOpenAccessFailure(player, access);
            return;
        }

        String regionId = access.regionId();
        if (regionId == null || regionId.isBlank()) {
            this.messageService.send(player, "commands.farmer.open-no-region");
            return;
        }

        prepareFarmer(provider, regionId).whenComplete((farmer, throwable) -> this.schedulerAdapter.runAtEntity(player, () -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Farmer menu open failed: " + readableMessage(throwable));
                this.messageService.send(player, "commands.farmer.open-failed");
                return;
            }
            if (farmer == null || farmer.isEmpty()) {
                this.messageService.send(player, "commands.farmer.open-no-farmer");
                return;
            }

            Farmer value = farmer.get();
            FarmerRole role = roleFor(player.getUniqueId(), value, access);
            boolean trusted = access.memberOptional().map(RegionMemberInfo::trusted).orElse(false);
            openResolved(player, menuId, previousMenuId, new FarmerMenuSession(value, role, trusted));
        }));
    }

    private CompletableFuture<Optional<Farmer>> prepareFarmer(RegionProvider provider, String regionId) {
        return this.databaseManager.readyFuture()
            .thenCompose(ignored -> reconcileIfNeeded(provider, regionId))
            .thenCompose(ignored -> this.farmerPersistenceService.findByRegionId(regionId));
    }

    private CompletableFuture<Void> reconcileIfNeeded(RegionProvider provider, String regionId) {
        if (!this.configManager.reconcileOnMenuOpen() || !provider.syncMembersOnOpen() || this.farmerReconcileService == null) {
            return CompletableFuture.completedFuture(null);
        }

        return this.farmerReconcileService.reconcileRegion(regionId).handle((result, throwable) -> {
            if (throwable != null) {
                this.debugLogger.debug("Menu open reconcile failed for " + regionId + ": " + readableMessage(throwable));
            }
            return null;
        });
    }

    private boolean openResolved(Player player, String menuId, String previousMenuId, FarmerMenuSession session) {
        if (player == null || session == null) {
            return false;
        }

        String normalizedMenuId = normalizeMenuId(menuId);
        String normalizedPreviousMenuId = normalizePreviousMenuId(previousMenuId);
        FarmerMenu menu = normalizedMenuId == null ? null : this.menus.get(normalizedMenuId);
        if (menu == null) {
            this.debugLogger.debug("Menu open skipped because menu is not registered: " + menuId);
            return false;
        }
        if (!session.canOpen(menu.requiredAccess())) {
            this.messageService.send(player, "commands.farmer.gui-denied");
            return false;
        }
        if (this.configManager.guiMenu(menu.id()) == null) {
            this.debugLogger.debug("Menu open skipped because layout is not configured: " + menu.id());
            this.messageService.send(player, "commands.farmer.open-failed");
            return false;
        }

        this.schedulerAdapter.runAtEntity(player, () -> openNow(player, menu, normalizedPreviousMenuId, session));
        return true;
    }

    private boolean back(MenuContext context, MenuAction action) {
        Optional<FarmerMenuSession> session = context.session();
        if (session.isEmpty()) {
            return close(context.player());
        }
        return context.holder().previousMenuId()
            .map(previous -> openResolved(context.player(), previous, null, session.get()))
            .orElseGet(() -> close(context.player()));
    }

    private boolean close(Player player) {
        if (player == null) {
            return false;
        }
        this.schedulerAdapter.runAtEntity(player, player::closeInventory);
        return true;
    }

    private boolean deferFutureAction(MenuContext context, MenuAction action) {
        this.messageService.send(context.player(), "commands.farmer.gui-action-pending");
        this.debugLogger.debug("Menu action is reserved for a later package: " + action.type() + ":" + action.target());
        return true;
    }

    private void openNow(Player player, FarmerMenu menu, String previousMenuId, FarmerMenuSession session) {
        if (!player.isOnline()) {
            return;
        }

        ConfigurationSection menuSection = this.configManager.guiMenu(menu.id());
        if (menuSection == null) {
            this.debugLogger.debug("Menu layout disappeared before open: " + menu.id());
            return;
        }

        Map<String, String> placeholders = placeholders(player, session);
        int size = menuSize(menuSection);
        String title = menuSection.getString("title", DEFAULT_TITLE);
        MenuLayoutBuilder builder = new MenuLayoutBuilder(menu.id(), previousMenuId, session, size, title);
        loadStaticItems(menuSection, builder, placeholders);
        menu.render(new MenuRenderContext(player, session, this.configManager, menuSection, placeholders), builder);

        MenuLayoutBuilder.MenuLayout layout = builder.build();
        Inventory inventory = Bukkit.createInventory(layout.holder(), layout.size(), TextUtil.parse(layout.title()));
        layout.holder().bind(inventory);
        layout.items().forEach(inventory::setItem);
        player.openInventory(inventory);
    }

    private void loadStaticItems(ConfigurationSection menuSection, MenuLayoutBuilder builder, Map<String, String> placeholders) {
        ConfigurationSection itemSection = menuSection.getConfigurationSection("items");
        if (itemSection == null) {
            return;
        }

        for (String slotKey : itemSection.getKeys(false)) {
            int slot = parseSlot(slotKey);
            if (slot < 0) {
                continue;
            }
            builder.putConfiguredItem(slot, itemSection.getConfigurationSection(slotKey), placeholders);
        }
    }

    private Map<String, String> placeholders(Player player, FarmerMenuSession session) {
        Farmer farmer = session.farmer();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("farmer", farmer.farmerId());
        placeholders.put("region", farmer.regionId());
        placeholders.put("owner", playerName(farmer.ownerUuid()));
        placeholders.put("owner_uuid", farmer.ownerUuid().toString());
        placeholders.put("level", String.valueOf(farmer.level()));
        placeholders.put("members", String.valueOf(memberCount(farmer)));
        placeholders.put("storage_usage", formatAmount(storageTotal(farmer)));
        placeholders.put("collecting_state", this.configManager.guiCollectingState(farmer.collectingEnabled()));
        placeholders.put("role", this.configManager.guiRoleName(session.role()));
        placeholders.put("player", player.getName());
        return Map.copyOf(placeholders);
    }

    private FarmerRole roleFor(UUID playerUuid, Farmer farmer, RegionAccessResult access) {
        if (playerUuid != null && playerUuid.equals(farmer.ownerUuid())) {
            return FarmerRole.OWNER;
        }
        Optional<FarmerRole> regionRole = access.memberOptional().map(RegionMemberInfo::role);
        if (regionRole.isPresent()) {
            return regionRole.get();
        }
        return farmer.members().values().stream()
            .filter(member -> member.playerUuid().equals(playerUuid))
            .map(member -> member.role())
            .findFirst()
            .orElse(FarmerRole.VIEWER);
    }

    private void sendOpenAccessFailure(Player player, RegionAccessResult access) {
        if (access.denyReason() == RegionAccessResult.DenyReason.PROVIDER_DISABLED) {
            this.messageService.send(player, "commands.farmer.provider-unavailable");
            return;
        }
        if (access.denyReason() == RegionAccessResult.DenyReason.PLAYER_NOT_MEMBER || access.denyReason() == RegionAccessResult.DenyReason.BANNED) {
            this.messageService.send(player, "commands.farmer.open-denied");
            return;
        }
        this.messageService.send(player, "commands.farmer.open-no-region");
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

    private int menuSize(ConfigurationSection menuSection) {
        int configuredSize = menuSection.getInt("size", 0);
        if (configuredSize > 0) {
            return normalizeInventorySize(configuredSize);
        }
        return clamp(menuSection.getInt("rows", 6), 1, 6) * 9;
    }

    private int normalizeInventorySize(int configuredSize) {
        int clamped = clamp(configuredSize, 9, 54);
        int rounded = ((clamped + 8) / 9) * 9;
        return clamp(rounded, 9, 54);
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

    private int memberCount(Farmer farmer) {
        return (int) farmer.members().keySet().stream().filter(uuid -> !uuid.equals(farmer.ownerUuid())).count() + 1;
    }

    private long storageTotal(Farmer farmer) {
        return farmer.storage().snapshot().values().stream().mapToLong(Long::longValue).sum();
    }

    private String formatAmount(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private String playerName(UUID playerUuid) {
        if (playerUuid == null) {
            return "-";
        }
        String name = Bukkit.getOfflinePlayer(playerUuid).getName();
        return name == null || name.isBlank() ? playerUuid.toString() : name;
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }
}
