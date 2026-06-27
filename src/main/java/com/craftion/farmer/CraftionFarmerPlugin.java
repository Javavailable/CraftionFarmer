package com.craftion.farmer;

import com.craftion.farmer.command.FarmerCommand;
import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.config.MessageManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.message.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftionFarmerPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private MessageService messageService;
    private DebugLogger debugLogger;

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

        if (!registerCommands()) {
            return;
        }

        this.debugLogger.debug("Debug mode is enabled.");
        getLogger().info("CraftionFarmer has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("CraftionFarmer has been disabled.");
    }

    public void reloadPluginFiles() {
        this.configManager.reload();
        this.messageManager.reload();
        this.debugLogger.debug("Configuration files reloaded.");
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
