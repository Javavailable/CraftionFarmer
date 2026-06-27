package com.craftion.farmer;

import com.craftion.farmer.command.FarmerCommand;
import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.config.MessageManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.hook.region.RegionProviderManager;
import com.craftion.farmer.message.MessageService;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import com.craftion.farmer.scheduler.SchedulerFactory;
import com.craftion.farmer.storage.DatabaseManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftionFarmerPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private MessageService messageService;
    private DebugLogger debugLogger;
    private SchedulerAdapter schedulerAdapter;
    private DatabaseManager databaseManager;
    private RegionProviderManager regionProviderManager;

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
        this.schedulerAdapter = SchedulerFactory.create(this);
        this.regionProviderManager = new RegionProviderManager(this, this.configManager, this.debugLogger);
        this.databaseManager = new DatabaseManager(this, this.configManager, this.schedulerAdapter, this.debugLogger);

        if (!registerCommands()) {
            return;
        }

        this.debugLogger.debug("Debug mode is enabled.");
        this.debugLogger.debug("Scheduler adapter: " + this.schedulerAdapter.type());
        this.regionProviderManager.initialize();
        this.databaseManager.initialize();
        getLogger().info("CraftionFarmer has been enabled.");
    }

    @Override
    public void onDisable() {
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
        if (this.regionProviderManager != null) {
            this.regionProviderManager.reload();
        }
        if (this.databaseManager != null) {
            this.databaseManager.reload();
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

    private boolean registerCommands() {
        PluginCommand farmerCommand = getCommand("farmer");
        if (farmerCommand == null) {
            getLogger().severe("The farmer command is missing from plugin metadata.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        FarmerCommand command = new FarmerCommand(this, this.messageService);
        farmerCommand.setExecutor(command);
        farmerCommand.setTabCompleter(command);
        return true;
    }
}
