package com.craftion.farmer.hook.spawner;

import com.craftion.farmer.collect.CollectService;
import com.craftion.farmer.hook.region.RegionProvider;
import com.craftion.farmer.hook.region.RegionProviderManager;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Coordinates optional CraftionSpawner bridge registration and cache-safe lifecycle changes. */
public final class CraftionSpawnerBridgeManager implements Listener {

    public static final String ROUTER_KEY_NAME = "craftionspawner-output";
    private static final String PRIMARY_PLUGIN_NAME = "CraftionSpawner";
    private static final String LEGACY_PLUGIN_NAME = "SmartSpawner";

    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final BooleanSupplier spawnerAvailable;
    private final BooleanSupplier regionAvailable;
    private final Function<NamespacedKey, CraftionSpawnerBridgeHandle> bridgeFactory;
    private final Consumer<String> failureLogger;
    private CraftionSpawnerBridgeHandle bridge;
    private CompletableFuture<Void> pendingDrain = CompletableFuture.completedFuture(null);
    private boolean initialized;
    private boolean cacheReady;
    private boolean shutdown;
    private long cacheGeneration;

    public CraftionSpawnerBridgeManager(
        JavaPlugin plugin,
        RegionProviderManager regionProviderManager,
        CollectService collectService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.key = new NamespacedKey(plugin, ROUTER_KEY_NAME);
        this.spawnerAvailable = () -> pluginAvailable(plugin.getServer().getPluginManager());
        this.regionAvailable = () -> {
            RegionProvider provider = regionProviderManager.provider();
            return provider != null && provider.isAvailable();
        };
        this.failureLogger = message -> plugin.getLogger().warning("CraftionSpawner bridge: " + message);
        this.bridgeFactory = key -> CraftionSpawnerBridgeRegistration.create(
            key,
            Objects.requireNonNull(collectService, "collectService"),
            this.failureLogger
        );
    }

    CraftionSpawnerBridgeManager(
        NamespacedKey key,
        BooleanSupplier spawnerAvailable,
        BooleanSupplier regionAvailable,
        Function<NamespacedKey, CraftionSpawnerBridgeHandle> bridgeFactory,
        Consumer<String> failureLogger
    ) {
        this.plugin = null;
        this.key = Objects.requireNonNull(key, "key");
        this.spawnerAvailable = Objects.requireNonNull(spawnerAvailable, "spawnerAvailable");
        this.regionAvailable = Objects.requireNonNull(regionAvailable, "regionAvailable");
        this.bridgeFactory = Objects.requireNonNull(bridgeFactory, "bridgeFactory");
        this.failureLogger = Objects.requireNonNull(failureLogger, "failureLogger");
    }

    public synchronized void initialize() {
        if (this.initialized || this.shutdown) {
            return;
        }
        this.initialized = true;
        if (this.plugin != null) {
            this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        }
        refreshLocked();
    }

    public synchronized void pauseForReload() {
        if (this.shutdown) {
            return;
        }
        this.cacheReady = false;
        this.cacheGeneration++;
        appendDrain(detachLocked());
    }

    public synchronized CacheLoadGate beginCacheLoad() {
        this.cacheReady = false;
        long generation = ++this.cacheGeneration;
        appendDrain(detachLocked());
        return new CacheLoadGate(generation, this.pendingDrain);
    }

    public synchronized void cacheLoaded(long generation) {
        if (this.shutdown || generation != this.cacheGeneration) {
            return;
        }
        this.cacheReady = true;
        refreshLocked();
    }

    public synchronized void cacheLoadFailed(long generation) {
        if (generation != this.cacheGeneration) {
            return;
        }
        this.cacheReady = false;
        appendDrain(detachLocked());
    }

    public synchronized CompletableFuture<Void> shutdown() {
        if (this.shutdown) {
            return this.pendingDrain;
        }
        this.shutdown = true;
        this.cacheReady = false;
        this.cacheGeneration++;
        appendDrain(detachLocked());
        if (this.plugin != null) {
            org.bukkit.event.HandlerList.unregisterAll(this);
        }
        return this.pendingDrain;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (isSpawnerPlugin(event.getPlugin())) {
            synchronized (this) {
                refreshLocked();
            }
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (isSpawnerPlugin(event.getPlugin())) {
            synchronized (this) {
                appendDrain(detachLocked());
            }
        }
    }

    synchronized boolean registered() {
        return this.bridge != null;
    }

    NamespacedKey key() {
        return this.key;
    }

    private void refreshLocked() {
        if (!this.initialized || this.shutdown || !this.cacheReady || this.bridge != null) {
            return;
        }

        try {
            if (!this.spawnerAvailable.getAsBoolean() || !this.regionAvailable.getAsBoolean()) {
                return;
            }
            CraftionSpawnerBridgeHandle candidate = this.bridgeFactory.apply(this.key);
            if (candidate == null || !candidate.register()) {
                return;
            }
            this.bridge = candidate;
        } catch (RuntimeException | LinkageError failure) {
            logFailure("registration failed: " + readableMessage(failure));
        }
    }

    private CompletableFuture<Void> detachLocked() {
        CraftionSpawnerBridgeHandle current = this.bridge;
        this.bridge = null;
        if (current == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return Objects.requireNonNullElseGet(current.shutdown(), () -> CompletableFuture.completedFuture(null));
        } catch (RuntimeException | LinkageError failure) {
            logFailure("unregistration failed: " + readableMessage(failure));
            return CompletableFuture.completedFuture(null);
        }
    }

    private void appendDrain(CompletableFuture<Void> drain) {
        this.pendingDrain = CompletableFuture.allOf(this.pendingDrain, drain);
    }

    private void logFailure(String message) {
        try {
            this.failureLogger.accept(message);
        } catch (RuntimeException | LinkageError ignored) {
            // Optional integration logging is best-effort.
        }
    }

    private static boolean pluginAvailable(PluginManager pluginManager) {
        return enabled(pluginManager.getPlugin(PRIMARY_PLUGIN_NAME))
            || enabled(pluginManager.getPlugin(LEGACY_PLUGIN_NAME));
    }

    private static boolean enabled(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }

    private static boolean isSpawnerPlugin(Plugin plugin) {
        if (plugin == null) {
            return false;
        }
        String name = plugin.getName();
        return PRIMARY_PLUGIN_NAME.equals(name) || LEGACY_PLUGIN_NAME.equals(name);
    }

    private static String readableMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public record CacheLoadGate(long generation, CompletableFuture<Void> drained) {

        public CacheLoadGate {
            Objects.requireNonNull(drained, "drained");
        }
    }
}
