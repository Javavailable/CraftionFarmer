package com.craftion.farmer.gui;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.economy.StorageTransactionResult;
import com.craftion.farmer.economy.StorageTransactionService;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import com.craftion.farmer.farmer.FarmerRole;
import com.craftion.farmer.farmer.MaterialKey;
import com.craftion.farmer.gui.listener.MenuClickListener;
import com.craftion.farmer.gui.listener.MenuDragListener;
import com.craftion.farmer.hook.region.RegionAccessResult;
import com.craftion.farmer.hook.region.RegionMemberInfo;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.hook.skyllia.FarmerReconcileService;
import com.craftion.farmer.message.GuiTextService;
import com.craftion.farmer.message.MessageService;
import com.craftion.farmer.module.ModuleManager;
import com.craftion.farmer.module.ModuleStateResult;
import com.craftion.farmer.module.ProductionEstimate;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import com.craftion.farmer.storage.DatabaseManager;
import com.craftion.farmer.util.TextUtil;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuService {

    private static final String USE_PERMISSION = "craftionfarmer.use";
    private static final String DEFAULT_TITLE = "CraftionFarmer";
    private static final String MAIN_MENU_ID = "main";
    private static final String MAIN_MENU_PREFIX = MAIN_MENU_ID + ":";
    private static final String PRODUCT_MENU_ID = "product";
    private static final String PRODUCT_MENU_PREFIX = PRODUCT_MENU_ID + ":";
    private static final String AMOUNT_INPUT_KEY = "amount";
    private static final long DIALOG_MAX_AMOUNT = 16_777_216L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final DatabaseManager databaseManager;
    private final RegionProviderManager regionProviderManager;
    private final FarmerPersistenceService farmerPersistenceService;
    private final FarmerReconcileService farmerReconcileService;
    private final MessageService messageService;
    private final GuiTextService guiTextService;
    private final StorageTransactionService storageTransactionService;
    private final ModuleManager moduleManager;
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
        MessageService messageService,
        GuiTextService guiTextService,
        StorageTransactionService storageTransactionService,
        ModuleManager moduleManager
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
        this.guiTextService = guiTextService;
        this.storageTransactionService = storageTransactionService;
        this.moduleManager = moduleManager;
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
        registerMenu(new ProductMenu());
        registerMenu(new StorageMenu());
        registerMenu(new ManageMenu());
        registerMenu(new MembersMenu());
        registerMenu(new ModulesMenu());
    }

    private void registerMenu(FarmerMenu menu) {
        this.menus.put(menu.id(), menu);
    }

    private void registerDefaultActions() {
        this.actionRegistry.register(MenuAction.Type.OPEN, (context, action) -> {
            if (isProductMenuId(action.target())) {
                return openProductDialog(context, action);
            }
            return context.session()
                .map(session -> openResolved(context.player(), action.target(), context.menuId(), session))
                .orElseGet(() -> openForPlayer(context.player(), action.target(), context.menuId()));
        });
        this.actionRegistry.register(MenuAction.Type.CLOSE, (context, action) -> close(context.player()));
        this.actionRegistry.register(MenuAction.Type.BACK, this::back);
        this.actionRegistry.register(MenuAction.Type.INFO, (context, action) -> true);
        this.actionRegistry.register(MenuAction.Type.WITHDRAW, this::withdraw);
        this.actionRegistry.register(MenuAction.Type.WITHDRAW_DIALOG, this::openWithdrawDialog);
        this.actionRegistry.register(MenuAction.Type.SELL, this::sell);
        this.actionRegistry.register(MenuAction.Type.SELL_DIALOG, this::openSellDialog);
        this.actionRegistry.register(MenuAction.Type.MODULE_TOGGLE, this::toggleModule);
        this.actionRegistry.register(MenuAction.Type.COLLECT_TOGGLE, this::toggleCollect);
        this.actionRegistry.register(MenuAction.Type.PRODUCT_TOGGLE, this::toggleProductCollect);
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
        FarmerMenu menu = normalizedMenuId == null ? null : this.menus.get(menuKey(normalizedMenuId));
        if (menu == null) {
            this.debugLogger.debug("Menu open skipped because menu is not registered: " + menuId);
            return false;
        }
        if (PRODUCT_MENU_ID.equals(menu.id()) && productMaterialKey(normalizedMenuId).isEmpty()) {
            this.debugLogger.debug("Product menu open skipped because material is unsupported: " + menuId);
            this.messageService.send(player, "commands.farmer.open-failed");
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

        this.schedulerAdapter.runAtEntity(player, () -> openNow(player, normalizedMenuId, menu, normalizedPreviousMenuId, session));
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

    private boolean withdraw(MenuContext context, MenuAction action) {
        Optional<FarmerMenuSession> session = context.session();
        if (session.isEmpty()) {
            this.messageService.send(context.player(), "commands.farmer.gui-denied");
            return false;
        }

        StorageTransactionResult result = this.storageTransactionService.withdraw(context.player(), session.get(), action.target());
        sendWithdrawResult(context.player(), result);
        if (result.success()) {
            refreshCurrentMenu(context);
        }
        return true;
    }

    private boolean sell(MenuContext context, MenuAction action) {
        Optional<FarmerMenuSession> session = context.session();
        if (session.isEmpty()) {
            this.messageService.send(context.player(), "commands.farmer.gui-denied");
            return false;
        }

        StorageTransactionResult result = this.storageTransactionService.sell(context.player(), session.get(), action.target());
        sendSellResult(context.player(), result);
        if (result.success()) {
            refreshCurrentMenu(context);
        }
        return true;
    }

    private boolean openWithdrawDialog(MenuContext context, MenuAction action) {
        return openAmountDialog(context, action, DialogOperation.WITHDRAW);
    }

    private boolean openSellDialog(MenuContext context, MenuAction action) {
        return openAmountDialog(context, action, DialogOperation.SELL);
    }

    private boolean openProductDialog(MenuContext context, MenuAction action) {
        Optional<FarmerMenuSession> session = context.session();
        if (session.isEmpty()) {
            this.messageService.send(context.player(), "commands.farmer.gui-denied");
            return false;
        }

        Optional<MaterialKey> materialKey = productMaterialKey(action.target());
        if (materialKey.isEmpty()) {
            this.messageService.send(context.player(), "commands.farmer.open-failed");
            return true;
        }

        FarmerMenuSession menuSession = session.get();
        if (!menuSession.canOpen(FarmerMenuAccess.MEMBER)) {
            this.messageService.send(context.player(), "commands.farmer.gui-denied");
            return true;
        }

        showProductDialog(
            context.player(),
            materialKey.get(),
            context.menuId(),
            context.holder().previousMenuId().orElse(null),
            menuSession
        );
        return true;
    }

    private void showProductDialog(Player player, MaterialKey materialKey, String menuId, String previousMenuId, FarmerMenuSession session) {
        this.schedulerAdapter.runAtEntity(player, () -> showProductDialogNow(player, materialKey, menuId, previousMenuId, session));
    }

    private void showProductDialogNow(Player player, MaterialKey materialKey, String menuId, String previousMenuId, FarmerMenuSession session) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!isConfiguredProduct(materialKey)) {
            this.messageService.send(player, "commands.farmer.open-failed");
            openResolved(player, menuId, previousMenuId, session);
            return;
        }

        player.closeInventory();
        player.showDialog(productDialog(materialKey, menuId, previousMenuId, session));
    }

    private Dialog productDialog(MaterialKey materialKey, String menuId, String previousMenuId, FarmerMenuSession session) {
        Map<String, String> productPlaceholders = productPlaceholders(session.farmer(), materialKey);
        String title = this.guiTextService.text("gui.dialogs.product.title", "Product", productPlaceholders);
        String body = String.join("\n", this.guiTextService.list("gui.dialogs.product.body", List.of(), productPlaceholders));

        List<DialogBody> bodyItems = List.of(
            DialogBody.item(new ItemStack(material(materialKey).orElse(Material.BARREL)))
                .showDecorations(false)
                .showTooltip(true)
                .width(48)
                .height(48)
                .build(),
            DialogBody.plainMessage(TextUtil.parse(body), 300)
        );
        List<ActionButton> buttons = List.of(
            productButton(
                productDialogButtonText("toggle", "label", productPlaceholders),
                productDialogButtonText("toggle", "tooltip", productPlaceholders),
                150,
                (response, audience) -> handleProductToggle(materialKey, menuId, previousMenuId, session, audience)
            ),
            productButton(
                productDialogButtonText("sell-amount", "label", productPlaceholders),
                productDialogButtonText("sell-amount", "tooltip", productPlaceholders),
                150,
                (response, audience) -> handleProductAmountDialog(DialogOperation.SELL, materialKey, menuId, previousMenuId, session, audience)
            ),
            productButton(
                productDialogButtonText("withdraw-amount", "label", productPlaceholders),
                productDialogButtonText("withdraw-amount", "tooltip", productPlaceholders),
                150,
                (response, audience) -> handleProductAmountDialog(DialogOperation.WITHDRAW, materialKey, menuId, previousMenuId, session, audience)
            ),
            productButton(
                productDialogButtonText("sell-all", "label", productPlaceholders),
                productDialogButtonText("sell-all", "tooltip", productPlaceholders),
                150,
                (response, audience) -> handleProductTransaction(DialogOperation.SELL, materialKey, menuId, previousMenuId, session, audience)
            ),
            productButton(
                productDialogButtonText("withdraw-space", "label", productPlaceholders),
                productDialogButtonText("withdraw-space", "tooltip", productPlaceholders),
                150,
                (response, audience) -> handleProductTransaction(DialogOperation.WITHDRAW, materialKey, menuId, previousMenuId, session, audience)
            )
        );
        ActionButton exitButton = productButton(
            productDialogButtonText("back", "label", productPlaceholders),
            productDialogButtonText("back", "tooltip", productPlaceholders),
            150,
            (response, audience) -> handleProductDialogBack(menuId, previousMenuId, session, audience)
        );
        DialogBase base = DialogBase.builder(TextUtil.parse(title))
            .externalTitle(TextUtil.parse(title))
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(bodyItems)
            .build();

        return Dialog.create(factory -> factory.empty()
            .base(base)
            .type(DialogType.multiAction(buttons, exitButton, 2)));
    }

    private String productDialogButtonText(String buttonId, String key, Map<String, String> placeholders) {
        return this.guiTextService.text("gui.dialogs.product.buttons." + buttonId + "." + key, buttonId, placeholders);
    }

    private ActionButton productButton(String label, String tooltip, int width, io.papermc.paper.registry.data.dialog.action.DialogActionCallback callback) {
        return ActionButton.builder(TextUtil.parse(label))
            .tooltip(TextUtil.parse(tooltip))
            .width(width)
            .action(DialogAction.customClick(callback, dialogOptions()))
            .build();
    }

    private void handleProductDialogBack(String menuId, String previousMenuId, FarmerMenuSession session, Audience audience) {
        if (audience instanceof Player player) {
            this.schedulerAdapter.runAtEntity(player, () -> openResolved(player, menuId, previousMenuId, session));
        }
    }

    private void handleProductAmountDialog(DialogOperation operation, MaterialKey materialKey, String menuId, String previousMenuId, FarmerMenuSession session, Audience audience) {
        if (!(audience instanceof Player player)) {
            return;
        }

        this.schedulerAdapter.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!isConfiguredProduct(materialKey)) {
                this.messageService.send(player, "commands.farmer.open-failed");
                openResolved(player, menuId, previousMenuId, session);
                return;
            }

            FarmerMenuAccess requiredAccess = operation == DialogOperation.WITHDRAW ? withdrawAccess() : FarmerMenuAccess.MANAGER;
            if (!hasCurrentAccess(player, session, requiredAccess)) {
                this.messageService.send(player, "commands.farmer.gui-denied");
                showProductDialog(player, materialKey, menuId, previousMenuId, session);
                return;
            }

            AmountAvailability availability = amountAvailability(player, session, materialKey, operation);
            if (availability.status() != StorageTransactionResult.Status.SUCCESS) {
                StorageTransactionResult result = transactionResult(availability.status(), materialKey);
                if (operation == DialogOperation.WITHDRAW) {
                    sendWithdrawResult(player, result);
                } else {
                    sendSellResult(player, result);
                }
                showProductDialog(player, materialKey, menuId, previousMenuId, session);
                return;
            }

            long sliderMax = Math.min(availability.amount(), DIALOG_MAX_AMOUNT);
            player.showDialog(amountDialog(operation, materialKey, sliderMax, menuId, previousMenuId, session, true));
        });
    }

    private void handleProductTransaction(DialogOperation operation, MaterialKey materialKey, String menuId, String previousMenuId, FarmerMenuSession session, Audience audience) {
        if (!(audience instanceof Player player)) {
            return;
        }

        this.schedulerAdapter.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!isConfiguredProduct(materialKey)) {
                this.messageService.send(player, "commands.farmer.open-failed");
                openResolved(player, menuId, previousMenuId, session);
                return;
            }

            FarmerMenuAccess requiredAccess = operation == DialogOperation.WITHDRAW ? withdrawAccess() : FarmerMenuAccess.MANAGER;
            if (!hasCurrentAccess(player, session, requiredAccess)) {
                this.messageService.send(player, "commands.farmer.gui-denied");
                showProductDialog(player, materialKey, menuId, previousMenuId, session);
                return;
            }

            StorageTransactionResult result = operation == DialogOperation.WITHDRAW
                ? this.storageTransactionService.withdraw(player, session, materialKey + ":all")
                : this.storageTransactionService.sell(player, session, materialKey + ":all");
            if (operation == DialogOperation.WITHDRAW) {
                sendWithdrawResult(player, result);
            } else {
                sendSellResult(player, result);
            }
            showProductDialog(player, materialKey, menuId, previousMenuId, session);
        });
    }

    private void handleProductToggle(MaterialKey materialKey, String menuId, String previousMenuId, FarmerMenuSession session, Audience audience) {
        if (!(audience instanceof Player player)) {
            return;
        }

        this.schedulerAdapter.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!isConfiguredProduct(materialKey)) {
                this.messageService.send(player, "commands.farmer.open-failed");
                openResolved(player, menuId, previousMenuId, session);
                return;
            }
            if (!hasCurrentAccess(player, session, FarmerMenuAccess.MANAGER)) {
                this.messageService.send(player, "commands.farmer.product-toggle-denied", materialPlaceholders(materialKey));
                showProductDialog(player, materialKey, menuId, previousMenuId, session);
                return;
            }

            Farmer farmer = session.farmer();
            boolean previousState = farmer.productCollectingEnabled(materialKey);
            boolean nextState = !previousState;
            farmer.setProductCollectingEnabled(materialKey, nextState);

            this.farmerPersistenceService.save(farmer).whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    farmer.setProductCollectingEnabled(materialKey, previousState);
                }

                this.schedulerAdapter.runAtEntity(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (throwable != null) {
                        this.plugin.getLogger().warning("Product collect toggle failed: " + readableMessage(throwable));
                        this.messageService.send(player, "commands.farmer.product-toggle-failed", materialPlaceholders(materialKey));
                        showProductDialog(player, materialKey, menuId, previousMenuId, session);
                        return;
                    }

                    this.messageService.send(
                        player,
                        nextState ? "commands.farmer.product-enabled" : "commands.farmer.product-disabled",
                        materialPlaceholders(materialKey)
                    );
                    showProductDialog(player, materialKey, menuId, previousMenuId, session);
                });
            });
        });
    }

    private boolean openAmountDialog(MenuContext context, MenuAction action, DialogOperation operation) {
        Optional<FarmerMenuSession> session = context.session();
        if (session.isEmpty()) {
            this.messageService.send(context.player(), "commands.farmer.gui-denied");
            return false;
        }

        Optional<MaterialKey> materialKey = materialKey(action.target());
        if (materialKey.isEmpty() || !isConfiguredProduct(materialKey.get())) {
            this.messageService.send(context.player(), "commands.farmer.open-failed");
            return false;
        }

        FarmerMenuSession menuSession = session.get();
        FarmerMenuAccess requiredAccess = operation == DialogOperation.WITHDRAW ? withdrawAccess() : FarmerMenuAccess.MANAGER;
        if (!hasCurrentAccess(context.player(), menuSession, requiredAccess)) {
            this.messageService.send(context.player(), "commands.farmer.gui-denied");
            return true;
        }

        AmountAvailability availability = amountAvailability(context.player(), menuSession, materialKey.get(), operation);
        if (availability.status() != StorageTransactionResult.Status.SUCCESS) {
            StorageTransactionResult result = transactionResult(availability.status(), materialKey.get());
            if (operation == DialogOperation.WITHDRAW) {
                sendWithdrawResult(context.player(), result);
            } else {
                sendSellResult(context.player(), result);
            }
            return true;
        }

        long sliderMax = Math.min(availability.amount(), DIALOG_MAX_AMOUNT);
        Dialog dialog = amountDialog(operation, materialKey.get(), sliderMax, context.menuId(), context.holder().previousMenuId().orElse(null), menuSession, false);
        this.schedulerAdapter.runAtEntity(context.player(), () -> {
            if (!context.player().isOnline()) {
                return;
            }
            context.player().closeInventory();
            context.player().showDialog(dialog);
        });
        return true;
    }

    private boolean toggleProductCollect(MenuContext context, MenuAction action) {
        Optional<FarmerMenuSession> session = context.session();
        if (session.isEmpty()) {
            this.messageService.send(context.player(), "commands.farmer.gui-denied");
            return false;
        }

        Optional<MaterialKey> materialKey = materialKey(action.target());
        if (materialKey.isEmpty() || !isConfiguredProduct(materialKey.get())) {
            this.messageService.send(context.player(), "commands.farmer.open-failed");
            return true;
        }

        FarmerMenuSession menuSession = session.get();
        if (!FarmerMenuAccess.MANAGER.allows(menuSession.role())) {
            this.messageService.send(context.player(), "commands.farmer.product-toggle-denied", materialPlaceholders(materialKey.get()));
            return true;
        }

        Farmer farmer = menuSession.farmer();
        boolean previousState = farmer.productCollectingEnabled(materialKey.get());
        boolean nextState = !previousState;
        farmer.setProductCollectingEnabled(materialKey.get(), nextState);

        this.farmerPersistenceService.save(farmer).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                farmer.setProductCollectingEnabled(materialKey.get(), previousState);
            }

            this.schedulerAdapter.runAtEntity(context.player(), () -> {
                if (throwable != null) {
                    this.plugin.getLogger().warning("Product collect toggle failed: " + readableMessage(throwable));
                    this.messageService.send(context.player(), "commands.farmer.product-toggle-failed", materialPlaceholders(materialKey.get()));
                    refreshCurrentMenu(context);
                    return;
                }

                this.messageService.send(
                    context.player(),
                    nextState ? "commands.farmer.product-enabled" : "commands.farmer.product-disabled",
                    materialPlaceholders(materialKey.get())
                );
                refreshCurrentMenu(context);
            });
        });
        return true;
    }

    private boolean toggleModule(MenuContext context, MenuAction action) {
        Optional<FarmerMenuSession> session = context.session();
        if (session.isEmpty()) {
            this.messageService.send(context.player(), "commands.farmer.gui-denied");
            return false;
        }

        this.moduleManager.toggle(context.player(), session.get(), action.target()).whenComplete((result, throwable) -> {
            this.schedulerAdapter.runAtEntity(context.player(), () -> {
                if (throwable != null) {
                    this.plugin.getLogger().warning("Module toggle failed: " + readableMessage(throwable));
                    this.messageService.send(context.player(), "commands.farmer.module-toggle-failed", modulePlaceholders(action.target(), false));
                    return;
                }
                sendModuleToggleResult(context.player(), result);
                if (result.success()) {
                    refreshCurrentMenu(context);
                }
            });
        });
        return true;
    }

    private boolean toggleCollect(MenuContext context, MenuAction action) {
        Optional<FarmerMenuSession> session = context.session();
        if (session.isEmpty()) {
            this.messageService.send(context.player(), "commands.farmer.gui-denied");
            return false;
        }

        FarmerMenuSession menuSession = session.get();
        if (!FarmerMenuAccess.MANAGER.allows(menuSession.role())) {
            this.messageService.send(context.player(), "commands.farmer.collect-toggle-denied");
            return true;
        }

        Farmer farmer = menuSession.farmer();
        boolean previousState = farmer.collectingEnabled();
        boolean nextState = !previousState;
        farmer.setCollectingEnabled(nextState);

        this.farmerPersistenceService.save(farmer).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                farmer.setCollectingEnabled(previousState);
            }

            this.schedulerAdapter.runAtEntity(context.player(), () -> {
                if (throwable != null) {
                    this.plugin.getLogger().warning("Collect toggle failed: " + readableMessage(throwable));
                    this.messageService.send(context.player(), "commands.farmer.collect-toggle-failed");
                    refreshCurrentMenu(context);
                    return;
                }

                this.messageService.send(context.player(), nextState ? "commands.farmer.collect-enabled" : "commands.farmer.collect-disabled");
                refreshCurrentMenu(context);
            });
        });
        return true;
    }

    private void sendWithdrawResult(Player player, StorageTransactionResult result) {
        String path = switch (result.status()) {
            case SUCCESS -> "commands.farmer.withdraw-success";
            case DENIED -> "commands.farmer.withdraw-denied";
            case EMPTY_STORAGE -> "commands.farmer.withdraw-empty";
            case INVENTORY_FULL -> "commands.farmer.withdraw-inventory-full";
            case INVALID_ACTION, NO_PRICE, ECONOMY_UNAVAILABLE, DEPOSIT_FAILED, FAILED -> "commands.farmer.withdraw-failed";
        };
        this.messageService.send(player, path, transactionPlaceholders(result));
    }

    private void sendSellResult(Player player, StorageTransactionResult result) {
        String path = switch (result.status()) {
            case SUCCESS -> "commands.farmer.sell-success";
            case DENIED -> "commands.farmer.sell-denied";
            case EMPTY_STORAGE -> "commands.farmer.sell-empty";
            case NO_PRICE -> "commands.farmer.sell-no-price";
            case ECONOMY_UNAVAILABLE -> "commands.farmer.sell-economy-unavailable";
            case DEPOSIT_FAILED -> "commands.farmer.sell-deposit-failed";
            case INVENTORY_FULL, INVALID_ACTION, FAILED -> "commands.farmer.sell-failed";
        };
        this.messageService.send(player, path, transactionPlaceholders(result));
    }

    private void sendModuleToggleResult(Player player, ModuleStateResult result) {
        String path = switch (result.status()) {
            case SUCCESS -> result.enabled() ? "commands.farmer.module-enabled" : "commands.farmer.module-disabled";
            case DENIED -> "commands.farmer.module-toggle-denied";
            case PERMISSION_DENIED -> "commands.farmer.module-permission-denied";
            case UNAVAILABLE -> "commands.farmer.module-unavailable";
            case UNKNOWN_MODULE -> "commands.farmer.module-unknown";
            case MODULE_DISABLED -> "commands.farmer.module-config-disabled";
            case FAILED -> "commands.farmer.module-toggle-failed";
        };
        this.messageService.send(player, path, modulePlaceholders(result.moduleKey(), result.enabled()));
    }

    private AmountAvailability amountAvailability(Player player, FarmerMenuSession session, MaterialKey materialKey, DialogOperation operation) {
        if (player == null || session == null || materialKey == null) {
            return new AmountAvailability(StorageTransactionResult.Status.INVALID_ACTION, 0L);
        }

        long storedAmount = session.farmer().storageAmount(materialKey);
        if (operation == DialogOperation.WITHDRAW) {
            if (!withdrawAccess().allows(session.role())) {
                return new AmountAvailability(StorageTransactionResult.Status.DENIED, 0L);
            }
            if (storedAmount <= 0L) {
                return new AmountAvailability(StorageTransactionResult.Status.EMPTY_STORAGE, 0L);
            }

            long amount = this.storageTransactionService.withdrawableAmount(player, session, materialKey);
            if (amount <= 0L) {
                return new AmountAvailability(StorageTransactionResult.Status.INVENTORY_FULL, 0L);
            }
            return new AmountAvailability(StorageTransactionResult.Status.SUCCESS, amount);
        }

        if (!FarmerMenuAccess.MANAGER.allows(session.role())) {
            return new AmountAvailability(StorageTransactionResult.Status.DENIED, 0L);
        }
        if (storedAmount <= 0L) {
            return new AmountAvailability(StorageTransactionResult.Status.EMPTY_STORAGE, 0L);
        }
        if (this.configManager.price(materialKey).isEmpty()) {
            return new AmountAvailability(StorageTransactionResult.Status.NO_PRICE, 0L);
        }
        long sellableAmount = this.storageTransactionService.sellableAmount(session, materialKey);
        if (sellableAmount <= 0L) {
            return new AmountAvailability(StorageTransactionResult.Status.NO_PRICE, 0L);
        }
        return new AmountAvailability(StorageTransactionResult.Status.SUCCESS, sellableAmount);
    }

    private Dialog amountDialog(DialogOperation operation, MaterialKey materialKey, long maxAmount, String menuId, String previousMenuId, FarmerMenuSession session, boolean returnToProductDialog) {
        String operationKey = operation.name().toLowerCase(Locale.ROOT);
        Map<String, String> dialogPlaceholders = amountDialogPlaceholders(operation, materialKey, maxAmount);
        String title = this.guiTextService.text("gui.dialogs.amount." + operationKey + ".title", operationKey, dialogPlaceholders);
        String body = String.join("\n", this.guiTextService.list("gui.dialogs.amount." + operationKey + ".body", List.of(), dialogPlaceholders));

        ActionButton confirmButton = ActionButton.builder(TextUtil.parse(amountDialogButtonText("confirm", "label", dialogPlaceholders)))
            .tooltip(TextUtil.parse(amountDialogButtonText("confirm", "tooltip", dialogPlaceholders)))
            .width(150)
            .action(DialogAction.customClick(
                (response, audience) -> handleAmountDialogResponse(
                    operation,
                    materialKey,
                    maxAmount,
                    menuId,
                    previousMenuId,
                    session,
                    returnToProductDialog,
                    response,
                    audience
                ),
                dialogOptions()
            ))
            .build();
        ActionButton cancelButton = ActionButton.builder(TextUtil.parse(amountDialogButtonText("cancel", "label", dialogPlaceholders)))
            .tooltip(TextUtil.parse(amountDialogButtonText("cancel", "tooltip", dialogPlaceholders)))
            .width(150)
            .action(DialogAction.customClick(
                (response, audience) -> handleAmountDialogCancel(materialKey, menuId, previousMenuId, session, returnToProductDialog, audience),
                dialogOptions()
            ))
            .build();
        DialogBase base = DialogBase.builder(TextUtil.parse(title))
            .externalTitle(TextUtil.parse(title))
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(List.of(DialogBody.plainMessage(TextUtil.parse(body), 300)))
            .inputs(List.of(DialogInput.numberRange(
                    AMOUNT_INPUT_KEY,
                    TextUtil.parse(this.guiTextService.text("gui.dialogs.amount.input-label", "Amount", dialogPlaceholders)),
                    1.0F,
                    (float) maxAmount
                )
                .width(300)
                .labelFormat("%s • %s")
                .initial((float) maxAmount)
                .step(1.0F)
                .build()))
            .build();

        return Dialog.create(factory -> factory.empty()
            .base(base)
            .type(DialogType.confirmation(confirmButton, cancelButton)));
    }

    private Map<String, String> amountDialogPlaceholders(DialogOperation operation, MaterialKey materialKey, long maxAmount) {
        OptionalDouble price = this.configManager.price(materialKey);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("material", materialKey == null ? "" : materialKey.toString());
        placeholders.put("material_name", materialName(materialKey));
        placeholders.put("max_amount", formatAmount(maxAmount));
        placeholders.put("max_worth", price.isPresent() ? formatMoney(price.getAsDouble() * maxAmount) : "-");
        placeholders.put("operation", this.guiTextService.action(operation == DialogOperation.WITHDRAW ? "withdraw" : "sell", operation.name().toLowerCase(Locale.ROOT)));
        return Map.copyOf(placeholders);
    }

    private String amountDialogButtonText(String buttonId, String key, Map<String, String> placeholders) {
        return this.guiTextService.text("gui.dialogs.amount.buttons." + buttonId + "." + key, buttonId, placeholders);
    }

    private ClickCallback.Options dialogOptions() {
        return ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2L))
            .build();
    }

    private void handleAmountDialogResponse(
        DialogOperation operation,
        MaterialKey materialKey,
        long maxAmount,
        String menuId,
        String previousMenuId,
        FarmerMenuSession session,
        boolean returnToProductDialog,
        DialogResponseView response,
        Audience audience
    ) {
        if (!(audience instanceof Player player)) {
            return;
        }

        long requestedAmount = selectedAmount(response.getFloat(AMOUNT_INPUT_KEY), maxAmount);
        this.schedulerAdapter.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!isConfiguredProduct(materialKey)) {
                this.messageService.send(player, "commands.farmer.open-failed");
                returnAfterAmountDialog(player, materialKey, menuId, previousMenuId, session, returnToProductDialog);
                return;
            }

            FarmerMenuAccess requiredAccess = operation == DialogOperation.WITHDRAW ? withdrawAccess() : FarmerMenuAccess.MANAGER;
            if (!hasCurrentAccess(player, session, requiredAccess)) {
                this.messageService.send(player, "commands.farmer.gui-denied");
                returnAfterAmountDialog(player, materialKey, menuId, previousMenuId, session, returnToProductDialog);
                return;
            }

            AmountAvailability availability = amountAvailability(player, session, materialKey, operation);
            if (availability.status() != StorageTransactionResult.Status.SUCCESS) {
                StorageTransactionResult result = transactionResult(availability.status(), materialKey);
                if (operation == DialogOperation.WITHDRAW) {
                    sendWithdrawResult(player, result);
                } else {
                    sendSellResult(player, result);
                }
                returnAfterAmountDialog(player, materialKey, menuId, previousMenuId, session, returnToProductDialog);
                return;
            }

            long amount = Math.min(requestedAmount, availability.amount());
            if (amount <= 0L) {
                StorageTransactionResult result = transactionResult(StorageTransactionResult.Status.INVALID_ACTION, materialKey);
                if (operation == DialogOperation.WITHDRAW) {
                    sendWithdrawResult(player, result);
                } else {
                    sendSellResult(player, result);
                }
                returnAfterAmountDialog(player, materialKey, menuId, previousMenuId, session, returnToProductDialog);
                return;
            }

            StorageTransactionResult result = operation == DialogOperation.WITHDRAW
                ? this.storageTransactionService.withdraw(player, session, materialKey, amount)
                : this.storageTransactionService.sell(player, session, materialKey, amount);
            if (operation == DialogOperation.WITHDRAW) {
                sendWithdrawResult(player, result);
            } else {
                sendSellResult(player, result);
            }
            returnAfterAmountDialog(player, materialKey, menuId, previousMenuId, session, returnToProductDialog);
        });
    }

    private void handleAmountDialogCancel(MaterialKey materialKey, String menuId, String previousMenuId, FarmerMenuSession session, boolean returnToProductDialog, Audience audience) {
        if (audience instanceof Player player) {
            this.schedulerAdapter.runAtEntity(
                player,
                () -> returnAfterAmountDialog(player, materialKey, menuId, previousMenuId, session, returnToProductDialog)
            );
        }
    }

    private void returnAfterAmountDialog(Player player, MaterialKey materialKey, String menuId, String previousMenuId, FarmerMenuSession session, boolean returnToProductDialog) {
        if (returnToProductDialog) {
            showProductDialog(player, materialKey, menuId, previousMenuId, session);
            return;
        }
        openResolved(player, menuId, previousMenuId, session);
    }

    private long selectedAmount(Float value, long maxAmount) {
        if (value == null || !Float.isFinite(value)) {
            return 1L;
        }
        return clamp(Math.round(value), 1L, maxAmount);
    }

    private FarmerMenuAccess withdrawAccess() {
        return this.configManager.allowMemberWithdraw() ? FarmerMenuAccess.MEMBER : FarmerMenuAccess.MANAGER;
    }

    private boolean hasCurrentAccess(Player player, FarmerMenuSession session, FarmerMenuAccess requiredAccess) {
        if (player == null || session == null || !player.isOnline()) {
            return false;
        }
        RegionProvider provider = this.regionProviderManager.provider();
        if (provider == null || !provider.isAvailable()) {
            return false;
        }
        RegionAccessResult access = provider.access(player.getLocation(), player.getUniqueId());
        if (!access.allowed() || access.regionId() == null || !access.regionId().equals(session.farmer().regionId())) {
            return false;
        }
        FarmerRole currentRole = roleFor(player.getUniqueId(), session.farmer(), access);
        return requiredAccess == null || requiredAccess.allows(currentRole);
    }

    private StorageTransactionResult transactionResult(StorageTransactionResult.Status status, MaterialKey materialKey) {
        return new StorageTransactionResult(status, materialKey, 0L, 0L, 0.0D, 0.0D, 0.0D, "", "");
    }

    private Map<String, String> transactionPlaceholders(StorageTransactionResult result) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("material", materialName(result.materialKey()));
        placeholders.put("amount", formatAmount(result.amount()));
        placeholders.put("materials", formatAmount(result.materialCount()));
        placeholders.put("gross", formatMoney(result.gross()));
        placeholders.put("tax", formatMoney(result.tax()));
        placeholders.put("net", formatMoney(result.net()));
        placeholders.put("provider", result.providerName().isBlank() ? "-" : result.providerName());
        placeholders.put("error", result.errorMessage().isBlank() ? this.guiTextService.label("unknown-error", "unknown error") : result.errorMessage());
        return Map.copyOf(placeholders);
    }

    private Map<String, String> modulePlaceholders(String moduleKey, boolean enabled) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("module", this.guiTextService.moduleName(moduleKey));
        placeholders.put("module_key", moduleKey == null ? "" : moduleKey);
        placeholders.put("module_state", this.guiTextService.state(enabled ? "active" : "closed", enabled ? "active" : "closed"));
        placeholders.put("permission", this.configManager.modulePermission(moduleKey));
        return Map.copyOf(placeholders);
    }

    private Map<String, String> materialPlaceholders(MaterialKey materialKey) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("material", materialKey == null ? "" : materialKey.toString());
        placeholders.put("material_name", materialName(materialKey));
        return Map.copyOf(placeholders);
    }

    private Map<String, String> productPlaceholders(Farmer farmer, MaterialKey materialKey) {
        long amount = farmer.storageAmount(materialKey);
        OptionalDouble price = this.configManager.price(materialKey);
        long capacity = this.configManager.maxStoragePerItem();
        boolean productEnabled = farmer.productCollectingEnabled(materialKey);
        boolean effectiveEnabled = farmer.collectingEnabled() && productEnabled;
        ProductionEstimate productionEstimate = this.moduleManager.productionEstimate(farmer);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("material", materialKey.toString());
        placeholders.put("material_name", materialName(materialKey));
        placeholders.put("amount", formatAmount(amount));
        placeholders.put("price", price.isPresent() ? formatMoney(price.getAsDouble()) : "-");
        placeholders.put("worth", price.isPresent() ? formatMoney(price.getAsDouble() * amount) : "-");
        placeholders.put("capacity", capacity < 0L ? this.guiTextService.state("unlimited", "unlimited") : formatAmount(capacity));
        placeholders.put("fill_percent", fillPercent(amount, capacity));
        placeholders.put("product_state", this.guiTextService.state(productEnabled ? "active" : "closed", productEnabled ? "active" : "closed"));
        placeholders.put("product_action", this.guiTextService.action(productEnabled ? "close" : "open", productEnabled ? "close" : "open"));
        placeholders.put("effective_product_state", this.guiTextService.state(effectiveEnabled ? "active" : "closed", effectiveEnabled ? "active" : "closed"));
        placeholders.put("price_state", price.isPresent() ? formatMoney(price.getAsDouble()) : this.guiTextService.state("no-price", "no price"));
        placeholders.put("collection_status", productCollectionStatus(farmer, materialKey));
        placeholders.put("product_material", material(materialKey).map(Material::name).orElse("BARREL"));
        placeholders.put("production_minute", formatAmount(productionEstimate.perMinute()));
        placeholders.put("production_hour", formatAmount(productionEstimate.perHour()));
        placeholders.put("production_day", formatAmount(productionEstimate.perDay()));
        return Map.copyOf(placeholders);
    }

    private String productCollectionStatus(Farmer farmer, MaterialKey materialKey) {
        if (!farmer.collectingEnabled() || !farmer.productCollectingEnabled(materialKey)) {
            return this.guiTextService.state("closed", "closed");
        }
        return this.guiTextService.state("active", "active");
    }

    private Map<String, String> mergedPlaceholders(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> merged = new HashMap<>(base);
        if (extra != null) {
            merged.putAll(extra);
        }
        return Map.copyOf(merged);
    }

    private void refreshCurrentMenu(MenuContext context) {
        context.session().ifPresent(session -> openResolved(
            context.player(),
            context.menuId(),
            context.holder().previousMenuId().orElse(null),
            session
        ));
    }

    private String materialName(MaterialKey materialKey) {
        return materialKey == null ? this.guiTextService.label("all-products", "all products") : this.guiTextService.materialName(materialKey.toString());
    }

    private Optional<Material> material(MaterialKey materialKey) {
        if (materialKey == null) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(materialKey.toString().toUpperCase(Locale.ROOT));
        return material == null || material.isAir() ? Optional.empty() : Optional.of(material);
    }

    private void openNow(Player player, String menuId, FarmerMenu menu, String previousMenuId, FarmerMenuSession session) {
        if (!player.isOnline()) {
            return;
        }

        ConfigurationSection menuSection = this.configManager.guiMenu(menu.id());
        if (menuSection == null) {
            this.debugLogger.debug("Menu layout disappeared before open: " + menu.id());
            return;
        }

        MaterialKey productMaterialKey = productMaterialKey(menuId).orElse(null);
        Map<String, String> placeholders = placeholders(player, session);
        if (productMaterialKey != null) {
            placeholders = mergedPlaceholders(placeholders, productPlaceholders(session.farmer(), productMaterialKey));
        }
        int size = menuSize(menuSection);
        String title = this.guiTextService.menuTitle(menu.id(), menuSection, placeholders, DEFAULT_TITLE);
        MenuLayoutBuilder builder = new MenuLayoutBuilder(menuId, previousMenuId, session, this.guiTextService, size, title);
        loadStaticItems(menuSection, builder, placeholders);
        menu.render(new MenuRenderContext(player, menuId, session, this.configManager, this.guiTextService, this.moduleManager, menuSection, productMaterialKey, placeholders), builder);

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
        placeholders.put("collecting_state", this.guiTextService.state(farmer.collectingEnabled() ? "active" : "closed", farmer.collectingEnabled() ? "active" : "closed"));
        placeholders.put("collecting_action", this.guiTextService.action(farmer.collectingEnabled() ? "close" : "open", farmer.collectingEnabled() ? "close" : "open"));
        placeholders.put("role", this.guiTextService.roleName(session.role()));
        placeholders.put("player", player.getName());
        ProductionEstimate productionEstimate = this.moduleManager.productionEstimate(farmer);
        placeholders.put("production_minute", formatAmount(productionEstimate.perMinute()));
        placeholders.put("production_hour", formatAmount(productionEstimate.perHour()));
        placeholders.put("production_day", formatAmount(productionEstimate.perDay()));
        placeholders.put("auto_sell_interval", autoSellIntervalLabel());
        return Map.copyOf(placeholders);
    }

    private String autoSellIntervalLabel() {
        return this.guiTextService.format("seconds", "%seconds% sec", Map.of("seconds", String.valueOf(this.configManager.autoSellIntervalSeconds())));
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

    private String menuKey(String menuId) {
        if (menuId == null) {
            return "";
        }
        if (isProductMenuId(menuId)) {
            return PRODUCT_MENU_ID;
        }
        if (isMainMenuId(menuId)) {
            return MAIN_MENU_ID;
        }
        return menuId;
    }

    private boolean isMainMenuId(String menuId) {
        return MAIN_MENU_ID.equals(menuId) || (menuId != null && menuId.startsWith(MAIN_MENU_PREFIX));
    }

    private boolean isProductMenuId(String menuId) {
        return menuId != null && menuId.startsWith(PRODUCT_MENU_PREFIX);
    }

    private Optional<MaterialKey> productMaterialKey(String menuId) {
        if (!isProductMenuId(menuId)) {
            return Optional.empty();
        }
        Optional<MaterialKey> materialKey = materialKey(menuId.substring(PRODUCT_MENU_PREFIX.length()));
        return materialKey.filter(this::isConfiguredProduct);
    }

    private Optional<MaterialKey> materialKey(String value) {
        try {
            return Optional.of(MaterialKey.of(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean isConfiguredProduct(MaterialKey materialKey) {
        return materialKey != null && this.configManager.collectMaterialKeys().contains(materialKey);
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

    private long clamp(long value, long min, long max) {
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

    private String fillPercent(long amount, long capacity) {
        if (capacity < 0L) {
            return "-";
        }
        if (capacity <= 0L) {
            return "0%";
        }
        double percent = Math.max(0.0D, Math.min(100.0D, (amount * 100.0D) / capacity));
        return String.format(Locale.US, "%.1f%%", percent);
    }

    private String formatMoney(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
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

    private enum DialogOperation {
        WITHDRAW,
        SELL
    }

    private record AmountAvailability(StorageTransactionResult.Status status, long amount) {
    }
}
