package com.craftion.farmer;

import com.craftion.farmer.command.FarmerCommand;
import com.craftion.farmer.command.FarmerPaperCommand;
import com.craftion.farmer.collect.CollectService;
import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.config.ConfigValidationService;
import com.craftion.farmer.config.MessageManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.economy.ConfigPriceProvider;
import com.craftion.farmer.economy.EconomyProviderManager;
import com.craftion.farmer.economy.PriceProvider;
import com.craftion.farmer.economy.StorageTransactionService;
import com.craftion.farmer.farmer.FarmerCache;
import com.craftion.farmer.farmer.FarmerCreateService;
import com.craftion.farmer.farmer.FarmerPersistenceService;
import com.craftion.farmer.farmer.FarmerRemoveService;
import com.craftion.farmer.farmer.FarmerSaveRetryService;
import com.craftion.farmer.gui.MenuService;
import com.craftion.farmer.hook.placeholder.PlaceholderProviderManager;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.hook.skyllia.FarmerReconcileService;
import com.craftion.farmer.hook.skyllia.SkylliaSyncManager;
import com.craftion.farmer.hook.visual.VisualProviderManager;
import com.craftion.farmer.message.MessageService;
import com.craftion.farmer.module.ModuleManager;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import com.craftion.farmer.scheduler.SchedulerFactory;
import com.craftion.farmer.storage.DatabaseManager;
import com.craftion.farmer.storage.repository.DatabaseLogRepository;
import com.craftion.farmer.storage.repository.LogRepository;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftionFarmerPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private ConfigValidationService configValidationService;
    private MessageService messageService;
    private DebugLogger debugLogger;
    private SchedulerAdapter schedulerAdapter;
    private DatabaseManager databaseManager;
    private LogRepository logRepository;
    private RegionProviderManager regionProviderManager;
    private VisualProviderManager visualProviderManager;
    private EconomyProviderManager economyProviderManager;
    private PriceProvider priceProvider;
    private FarmerCache farmerCache;
    private FarmerPersistenceService farmerPersistenceService;
    private FarmerSaveRetryService farmerSaveRetryService;
    private FarmerCreateService farmerCreateService;
    private FarmerRemoveService farmerRemoveService;
    private FarmerReconcileService farmerReconcileService;
    private SkylliaSyncManager skylliaSyncManager;
    private MenuService menuService;
    private CollectService collectService;
    private StorageTransactionService storageTransactionService;
    private ModuleManager moduleManager;
    private PlaceholderProviderManager placeholderProviderManager;

    @Override
    public void onLoad() {
        getLogger().info("CraftionFarmer is loading.");
    }

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);

        this.configManager.reload();
        this.messageManager.reload();

        this.messageService = new MessageService(this.messageManager);
        this.debugLogger = new DebugLogger(this, this.configManager);
        this.configValidationService = new ConfigValidationService(this, this.debugLogger);
        this.schedulerAdapter = SchedulerFactory.create(this);
        this.regionProviderManager = new RegionProviderManager(this, this.configManager, this.debugLogger);
        this.visualProviderManager = new VisualProviderManager(this, this.configManager, this.schedulerAdapter, this.debugLogger, player -> {
            if (this.menuService != null) {
                this.menuService.openMain(player);
                return;
            }
            player.performCommand("farmer open");
        });
        this.databaseManager = new DatabaseManager(this, this.configManager, this.schedulerAdapter, this.debugLogger);
        this.logRepository = new DatabaseLogRepository(this.databaseManager);
        this.economyProviderManager = new EconomyProviderManager(this, this.configManager, this.debugLogger);
        this.priceProvider = new ConfigPriceProvider(this.configManager);
        this.farmerCache = new FarmerCache();
        this.farmerPersistenceService = new FarmerPersistenceService(this.databaseManager, this.farmerCache, this.debugLogger);
        this.farmerSaveRetryService = new FarmerSaveRetryService(this, this.schedulerAdapter, this.debugLogger, this.farmerPersistenceService, this.farmerCache);
        this.storageTransactionService = new StorageTransactionService(
            this,
            this.configManager,
            this.debugLogger,
            this.farmerPersistenceService,
            this.farmerSaveRetryService,
            this.logRepository,
            this.economyProviderManager,
            this.priceProvider
        );
        this.moduleManager = new ModuleManager(
            this.configManager,
            this.schedulerAdapter,
            this.debugLogger,
            this.farmerCache,
            this.farmerPersistenceService,
            this.farmerSaveRetryService,
            this.logRepository,
            this.regionProviderManager,
            this.economyProviderManager,
            this.storageTransactionService
        );
        this.placeholderProviderManager = new PlaceholderProviderManager(this, this.debugLogger, this.farmerCache, this.moduleManager);
        this.farmerCreateService = new FarmerCreateService(this.farmerPersistenceService, this.regionProviderManager, this.visualProviderManager);
        this.farmerRemoveService = new FarmerRemoveService(
            this.farmerPersistenceService,
            this.farmerSaveRetryService,
            this.regionProviderManager,
            this.visualProviderManager,
            Duration.ofSeconds(30L)
        );
        this.farmerReconcileService = new FarmerReconcileService(
            this.schedulerAdapter,
            this.databaseManager,
            this.farmerCache,
            this.regionProviderManager,
            this.visualProviderManager,
            this.debugLogger
        );
        this.skylliaSyncManager = new SkylliaSyncManager(this, this.configManager, this.farmerReconcileService, this.debugLogger);
        this.collectService = new CollectService(
            this,
            this.configManager,
            this.schedulerAdapter,
            this.debugLogger,
            this.regionProviderManager,
            this.farmerCache,
            this.farmerPersistenceService,
            this.moduleManager
        );
        this.menuService = new MenuService(
            this,
            this.configManager,
            this.schedulerAdapter,
            this.debugLogger,
            this.databaseManager,
            this.regionProviderManager,
            this.farmerPersistenceService,
            this.farmerReconcileService,
            this.messageService,
            this.storageTransactionService,
            this.moduleManager
        );

        if (!registerCommands()) {
            return;
        }

        this.debugLogger.debug("Debug mode is enabled.");
        this.debugLogger.debug("Scheduler adapter: " + this.schedulerAdapter.type());
        validateConfiguration();
        this.regionProviderManager.initialize();
        this.visualProviderManager.initialize();
        this.economyProviderManager.initialize();
        this.databaseManager.initialize();
        this.moduleManager.initialize();
        loadFarmerCacheWhenReady();
        this.skylliaSyncManager.initialize();
        this.collectService.initialize();
        this.menuService.initialize();
        this.placeholderProviderManager.initialize();
        getLogger().info("CraftionFarmer has been enabled.");
    }

    @Override
    public void onDisable() {
        if (this.skylliaSyncManager != null) {
            this.skylliaSyncManager.shutdown();
        }

        if (this.placeholderProviderManager != null) {
            this.placeholderProviderManager.shutdown();
        }

        if (this.moduleManager != null) {
            this.moduleManager.shutdown();
        }

        if (this.collectService != null) {
            this.collectService.shutdown();
        }

        this.menuService = null;

        flushDirtyFarmersOnDisable();
        saveCachedFarmersOnDisable();

        if (this.visualProviderManager != null) {
            this.visualProviderManager.shutdown();
        }

        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }

        if (this.schedulerAdapter != null) {
            this.schedulerAdapter.cancelTasks();
        }

        getLogger().info("CraftionFarmer has been disabled.");
    }

    public void reloadPluginFiles() {
        this.configManager.reload();
        this.messageManager.reload();
        validateConfiguration();
        if (this.regionProviderManager != null) {
            this.regionProviderManager.reload();
        }
        if (this.visualProviderManager != null) {
            this.visualProviderManager.reload();
        }
        if (this.economyProviderManager != null) {
            this.economyProviderManager.reload();
        }
        if (this.moduleManager != null) {
            this.moduleManager.reload();
        }
        if (this.placeholderProviderManager != null) {
            this.placeholderProviderManager.reload();
        }
        if (this.skylliaSyncManager != null) {
            this.skylliaSyncManager.reload();
        }
        if (this.collectService != null) {
            this.collectService.reload();
        }
        if (this.menuService != null) {
            this.menuService.reload();
        }
        if (this.databaseManager != null) {
            this.databaseManager.reload();
            loadFarmerCacheWhenReady();
        }
        this.debugLogger.debug("Configuration files reloaded.");
    }

    public SchedulerAdapter scheduler() {
        return this.schedulerAdapter;
    }

    public DatabaseManager database() {
        return this.databaseManager;
    }

    public RegionProviderManager regionProviderManager() {
        return this.regionProviderManager;
    }

    public VisualProviderManager visualProviderManager() {
        return this.visualProviderManager;
    }

    public ConfigManager configManager() {
        return this.configManager;
    }

    public FarmerCache farmerCache() {
        return this.farmerCache;
    }

    public FarmerPersistenceService farmerPersistenceService() {
        return this.farmerPersistenceService;
    }

    public FarmerSaveRetryService farmerSaveRetryService() {
        return this.farmerSaveRetryService;
    }

    public FarmerCreateService farmerCreateService() {
        return this.farmerCreateService;
    }

    public FarmerRemoveService farmerRemoveService() {
        return this.farmerRemoveService;
    }

    public FarmerReconcileService farmerReconcileService() {
        return this.farmerReconcileService;
    }

    public MenuService menuService() {
        return this.menuService;
    }

    public CollectService collectService() {
        return this.collectService;
    }

    public EconomyProviderManager economyProviderManager() {
        return this.economyProviderManager;
    }

    public StorageTransactionService storageTransactionService() {
        return this.storageTransactionService;
    }

    public ModuleManager moduleManager() {
        return this.moduleManager;
    }

    public PlaceholderProviderManager placeholderProviderManager() {
        return this.placeholderProviderManager;
    }

    public LogRepository logRepository() {
        return this.logRepository;
    }

    private void loadFarmerCacheWhenReady() {
        if (this.databaseManager == null || this.farmerPersistenceService == null) {
            return;
        }

        this.databaseManager.readyFuture().thenCompose(ignored -> this.farmerPersistenceService.loadAll()).whenComplete((farmers, throwable) -> {
            if (throwable != null) {
                getLogger().warning("Farmer cache yuklenemedi: " + readableMessage(throwable));
                return;
            }
            if (this.visualProviderManager != null) {
                this.visualProviderManager.reconcile(farmers);
            }
            if (this.moduleManager != null) {
                this.moduleManager.ensureDefaultStates(farmers);
            }
            this.debugLogger.debug("Farmer cache ready: " + farmers.size());
        });
    }

    private void saveCachedFarmersOnDisable() {
        if (this.databaseManager == null || this.farmerPersistenceService == null || !this.databaseManager.isAvailable()) {
            return;
        }

        try {
            this.farmerPersistenceService.saveAllCached().get(10L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            getLogger().warning("Farmer cache kaydedilemedi: " + readableMessage(exception));
        }
    }

    private void flushDirtyFarmersOnDisable() {
        if (this.farmerSaveRetryService == null) {
            return;
        }
        this.farmerSaveRetryService.flushNowBlocking(Duration.ofSeconds(10L));
    }

    private void validateConfiguration() {
        if (this.configValidationService != null) {
            this.configValidationService.validate();
        }
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }

    private boolean registerCommands() {
        FarmerPaperCommand command = new FarmerPaperCommand(new FarmerCommand(this, this.messageService));
        try {
            registerCommand("farmer", command);
            registerCommand("ciftci", command);
            return true;
        } catch (RuntimeException exception) {
            getLogger().severe("CraftionFarmer commands could not be registered: " + readableMessage(exception));
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }
}
