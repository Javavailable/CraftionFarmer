package com.craftion.farmer.hook.spawner;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftion.farmer.collect.CraftionSpawnerOutputRouter;
import github.nighter.smartspawner.api.output.SpawnerOutputRouterRegistry;
import java.util.concurrent.CompletableFuture;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class CraftionSpawnerBridgeRegistrationTest {

    @Test
    void duplicateRegistrationIsPreventedAndShutdownUsesSameExactKey() {
        NamespacedKey key = new NamespacedKey("craftionfarmer", "craftionspawner-output");
        SpawnerOutputRouterRegistry registry = mock(SpawnerOutputRouterRegistry.class);
        CraftionSpawnerOutputRouter router = mock(CraftionSpawnerOutputRouter.class);
        when(registry.isRegistered(key)).thenReturn(false, true);
        when(registry.register(same(key), eq(CraftionSpawnerBridgeRegistration.ROUTER_ORDER), same(router))).thenReturn(true);
        when(router.stopAccepting()).thenReturn(CompletableFuture.completedFuture(null));
        CraftionSpawnerBridgeRegistration registration = new CraftionSpawnerBridgeRegistration(key, registry, router);

        assertTrue(registration.register());
        assertTrue(registration.register());
        registration.shutdown().join();

        verify(router, times(1)).startAccepting();
        verify(registry, times(1)).register(same(key), eq(CraftionSpawnerBridgeRegistration.ROUTER_ORDER), same(router));
        verify(registry, times(1)).unregister(same(key));
    }

    @Test
    void staleRegistrationIsRemovedBeforeRegisteringReplacement() {
        NamespacedKey key = new NamespacedKey("craftionfarmer", "craftionspawner-output");
        SpawnerOutputRouterRegistry registry = mock(SpawnerOutputRouterRegistry.class);
        CraftionSpawnerOutputRouter router = mock(CraftionSpawnerOutputRouter.class);
        when(registry.isRegistered(key)).thenReturn(true);
        when(registry.register(same(key), eq(CraftionSpawnerBridgeRegistration.ROUTER_ORDER), same(router))).thenReturn(true);
        CraftionSpawnerBridgeRegistration registration = new CraftionSpawnerBridgeRegistration(key, registry, router);

        assertTrue(registration.register());

        verify(registry).unregister(same(key));
        verify(registry).register(same(key), eq(CraftionSpawnerBridgeRegistration.ROUTER_ORDER), same(router));
    }
}
