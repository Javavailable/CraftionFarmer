package com.craftion.farmer.hook.spawner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class CraftionSpawnerBridgeManagerTest {

    @Test
    void cacheNotReadyDoesNotRegisterAndDuplicateRefreshIsPrevented() {
        Fixture fixture = new Fixture();
        fixture.manager.initialize();

        assertEquals(0, fixture.handles.size());

        CraftionSpawnerBridgeManager.CacheLoadGate gate = fixture.manager.beginCacheLoad();
        fixture.manager.cacheLoaded(gate.generation());
        fixture.manager.cacheLoaded(gate.generation());
        fixture.manager.initialize();

        assertEquals(1, fixture.handles.size());
        assertEquals(1, fixture.handles.getFirst().registerCalls);
        assertTrue(fixture.manager.registered());
    }

    @Test
    void unavailableDependencyOrRegionDoesNotRegister() {
        Fixture fixture = new Fixture();
        fixture.spawnerAvailable.set(false);
        fixture.manager.initialize();
        CraftionSpawnerBridgeManager.CacheLoadGate first = fixture.manager.beginCacheLoad();
        fixture.manager.cacheLoaded(first.generation());
        assertFalse(fixture.manager.registered());

        fixture.spawnerAvailable.set(true);
        fixture.regionAvailable.set(false);
        fixture.manager.pauseForReload();
        CraftionSpawnerBridgeManager.CacheLoadGate second = fixture.manager.beginCacheLoad();
        fixture.manager.cacheLoaded(second.generation());

        assertFalse(fixture.manager.registered());
        assertEquals(0, fixture.handles.size());
    }

    @Test
    void reloadRemovesStaleRegistrationAndReusesExactKey() {
        Fixture fixture = new Fixture();
        fixture.manager.initialize();
        CraftionSpawnerBridgeManager.CacheLoadGate first = fixture.manager.beginCacheLoad();
        fixture.manager.cacheLoaded(first.generation());
        FakeHandle original = fixture.handles.getFirst();

        fixture.manager.pauseForReload();

        assertEquals(1, original.shutdownCalls);
        assertFalse(fixture.manager.registered());

        CraftionSpawnerBridgeManager.CacheLoadGate second = fixture.manager.beginCacheLoad();
        fixture.manager.cacheLoaded(second.generation());

        assertEquals(2, fixture.handles.size());
        assertSame(fixture.key, fixture.factoryKeys.get(0));
        assertSame(fixture.key, fixture.factoryKeys.get(1));
        assertTrue(fixture.manager.registered());
    }

    @Test
    void staleCacheCompletionCannotRegisterAfterNewGeneration() {
        Fixture fixture = new Fixture();
        fixture.manager.initialize();
        CraftionSpawnerBridgeManager.CacheLoadGate stale = fixture.manager.beginCacheLoad();
        CraftionSpawnerBridgeManager.CacheLoadGate current = fixture.manager.beginCacheLoad();

        fixture.manager.cacheLoaded(stale.generation());
        assertFalse(fixture.manager.registered());

        fixture.manager.cacheLoaded(current.generation());
        assertTrue(fixture.manager.registered());
    }

    @Test
    void shutdownUnregistersAndPreventsFutureRegistration() {
        Fixture fixture = new Fixture();
        fixture.manager.initialize();
        CraftionSpawnerBridgeManager.CacheLoadGate gate = fixture.manager.beginCacheLoad();
        fixture.manager.cacheLoaded(gate.generation());
        FakeHandle handle = fixture.handles.getFirst();

        fixture.manager.shutdown().join();
        fixture.manager.cacheLoaded(gate.generation());

        assertEquals(1, handle.shutdownCalls);
        assertFalse(fixture.manager.registered());
        assertEquals(1, fixture.handles.size());
    }

    private static final class Fixture {

        private final NamespacedKey key = new NamespacedKey("craftionfarmer", "craftionspawner-output");
        private final AtomicBoolean spawnerAvailable = new AtomicBoolean(true);
        private final AtomicBoolean regionAvailable = new AtomicBoolean(true);
        private final List<NamespacedKey> factoryKeys = new ArrayList<>();
        private final List<FakeHandle> handles = new ArrayList<>();
        private final CraftionSpawnerBridgeManager manager = new CraftionSpawnerBridgeManager(
            this.key,
            this.spawnerAvailable::get,
            this.regionAvailable::get,
            key -> {
                this.factoryKeys.add(key);
                FakeHandle handle = new FakeHandle();
                this.handles.add(handle);
                return handle;
            },
            ignored -> {
            }
        );
    }

    private static final class FakeHandle implements CraftionSpawnerBridgeHandle {

        private int registerCalls;
        private int shutdownCalls;

        @Override
        public boolean register() {
            this.registerCalls++;
            return true;
        }

        @Override
        public CompletableFuture<Void> shutdown() {
            this.shutdownCalls++;
            return CompletableFuture.completedFuture(null);
        }
    }
}
