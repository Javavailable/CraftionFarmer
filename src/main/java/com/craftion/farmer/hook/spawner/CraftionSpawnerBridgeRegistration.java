package com.craftion.farmer.hook.spawner;

import com.craftion.farmer.collect.CollectService;
import com.craftion.farmer.collect.CraftionSpawnerOutputRouter;
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerProvider;
import github.nighter.smartspawner.api.output.SpawnerOutputRouterRegistry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.NamespacedKey;

final class CraftionSpawnerBridgeRegistration implements CraftionSpawnerBridgeHandle {

    static final int ROUTER_ORDER = 100;

    private final NamespacedKey key;
    private final SpawnerOutputRouterRegistry registry;
    private final CraftionSpawnerOutputRouter router;
    private boolean registered;

    static CraftionSpawnerBridgeRegistration create(
        NamespacedKey key,
        CollectService collectService,
        Consumer<String> failureLogger
    ) {
        SmartSpawnerAPI api = SmartSpawnerProvider.getAPI();
        if (api == null) {
            return null;
        }
        SpawnerOutputRouterRegistry registry = api.getOutputRouterRegistry();
        if (registry == null) {
            return null;
        }
        return new CraftionSpawnerBridgeRegistration(
            key,
            registry,
            new CraftionSpawnerOutputRouter(collectService, failureLogger)
        );
    }

    CraftionSpawnerBridgeRegistration(
        NamespacedKey key,
        SpawnerOutputRouterRegistry registry,
        CraftionSpawnerOutputRouter router
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.router = Objects.requireNonNull(router, "router");
    }

    @Override
    public synchronized boolean register() {
        if (this.registered) {
            return true;
        }

        if (this.registry.isRegistered(this.key)) {
            this.registry.unregister(this.key);
        }

        this.router.startAccepting();
        this.registered = this.registry.register(this.key, ROUTER_ORDER, this.router);
        if (!this.registered) {
            this.router.stopAccepting();
        }
        return this.registered;
    }

    @Override
    public synchronized CompletableFuture<Void> shutdown() {
        CompletableFuture<Void> drained = this.router.stopAccepting();
        if (this.registered || this.registry.isRegistered(this.key)) {
            this.registry.unregister(this.key);
        }
        this.registered = false;
        return drained;
    }
}
