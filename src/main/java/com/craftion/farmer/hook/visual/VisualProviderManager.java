package com.craftion.farmer.hook.visual;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class VisualProviderManager {

    private static final String FANCY_NPCS_PLUGIN_NAME = "FancyNpcs";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final Consumer<Player> npcClickAction;
    private FarmerVisualProvider provider = new NoVisualProvider();

    public VisualProviderManager(
        JavaPlugin plugin,
        ConfigManager configManager,
        SchedulerAdapter schedulerAdapter,
        DebugLogger debugLogger,
        Consumer<Player> npcClickAction
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.schedulerAdapter = schedulerAdapter;
        this.debugLogger = debugLogger;
        this.npcClickAction = npcClickAction == null ? player -> player.performCommand("farmer open") : npcClickAction;
    }

    public void initialize() {
        this.provider = createProvider();
        this.debugLogger.debug("Visual provider: " + this.provider.type());
    }

    public void reload() {
        this.provider.shutdown();
        initialize();
    }

    public FarmerVisualProvider provider() {
        return this.provider;
    }

    public void spawn(Farmer farmer) {
        this.provider.spawn(farmer);
    }

    public void remove(Farmer farmer) {
        this.provider.remove(farmer);
    }

    public void remove(String farmerId) {
        this.provider.remove(farmerId);
    }

    public void reconcile(Collection<Farmer> farmers) {
        this.provider.reconcile(farmers);
    }

    public void shutdown() {
        this.provider.shutdown();
    }

    private FarmerVisualProvider createProvider() {
        if (!this.configManager.isNpcEnabled()) {
            this.plugin.getLogger().info("Visual provider disabled by npc.enabled config.");
            return new NoVisualProvider();
        }

        VisualProviderType type = VisualProviderType.from(this.configManager.visualProvider());
        if (type == VisualProviderType.NONE) {
            this.plugin.getLogger().info("Visual provider disabled by config.");
            return new NoVisualProvider();
        }

        if (type == VisualProviderType.FANCY_NPCS) {
            Plugin fancyNpcs = this.plugin.getServer().getPluginManager().getPlugin(FANCY_NPCS_PLUGIN_NAME);
            if (fancyNpcs == null || !fancyNpcs.isEnabled()) {
                this.plugin.getLogger().warning("FancyNPCs visual provider secildi fakat FancyNPCs plugin aktif degil. Visual provider devre disi birakildi.");
                return new NoVisualProvider();
            }

            Optional<FancyNpcsVisualProvider> provider = FancyNpcsVisualProvider.create(
                this.plugin,
                this.configManager,
                this.schedulerAdapter,
                this.debugLogger,
                this.npcClickAction
            );
            if (provider.isEmpty()) {
                this.plugin.getLogger().warning("FancyNPCs visual provider secildi fakat desteklenen FancyNPCs API bulunamadi. Visual provider devre disi birakildi.");
                return new NoVisualProvider();
            }

            return provider.get();
        }

        return new NoVisualProvider();
    }
}
