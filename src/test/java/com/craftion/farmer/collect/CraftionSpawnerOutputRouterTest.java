package com.craftion.farmer.collect;

import static com.craftion.farmer.test.MockItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.craftion.farmer.farmer.MaterialKey;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.output.SpawnerOutputContext;
import github.nighter.smartspawner.api.output.SpawnerOutputResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class CraftionSpawnerOutputRouterTest {

    private static final MaterialKey WHEAT = MaterialKey.of("WHEAT");

    @Test
    void fullAcceptanceReturnsZeroRemainderAndInvokesCollectionOnce() {
        AtomicInteger calls = new AtomicInteger();
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            calls.incrementAndGet();
            CollectResult result = result(context, CollectResult.Status.COLLECTED, context.itemStack().getAmount());
            tracker.record(result);
            return result;
        });

        SpawnerOutputResult result = router.route(context(item(Material.WHEAT, 64)));

        assertTrue(result.getRemainingItems().isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void partialAcceptanceReturnsExactRemainderAndPreservesMetadata() {
        ItemStack original = item(Material.WHEAT, 64, true);
        ItemMeta metadata = original.getItemMeta();
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            assertEquals(CollectReason.SPAWNER_OUTPUT, context.reason());
            assertFalse(context.playerDrop());
            CollectResult result = result(context, CollectResult.Status.PARTIAL, 40L);
            tracker.record(result);
            return result;
        });

        ItemStack remainder = router.route(context(original)).getRemainingItems().getFirst();

        assertEquals(24, remainder.getAmount());
        assertEquals(Material.WHEAT, remainder.getType());
        assertSame(metadata, remainder.getItemMeta());
        assertEquals(64, original.getAmount());
    }

    @Test
    void storageFullReturnsOriginalAmount() {
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            CollectResult result = result(context, CollectResult.Status.STORAGE_FULL, 0L);
            tracker.record(result);
            return result;
        });

        assertEquals(64, router.route(context(item(Material.WHEAT, 64))).getRemainingItems().getFirst().getAmount());
    }

    @Test
    void skippedDepositReturnsOriginalAmount() {
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            CollectResult result = CollectResult.skipped(CollectResult.Status.DISABLED, context);
            tracker.record(result);
            return result;
        });

        assertEquals(32, router.route(context(item(Material.WHEAT, 32))).getRemainingItems().getFirst().getAmount());
    }

    @Test
    void noFarmerReturnsOriginalAmount() {
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            CollectResult result = CollectResult.skipped(CollectResult.Status.NO_FARMER, context, "region-one");
            tracker.record(result);
            return result;
        });

        assertEquals(16, router.route(context(item(Material.WHEAT, 16))).getRemainingItems().getFirst().getAmount());
    }

    @Test
    void eachGeneratedStackIsCollectedExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            calls.incrementAndGet();
            CollectResult result = result(context, CollectResult.Status.PARTIAL, 2L);
            tracker.record(result);
            return result;
        });

        SpawnerOutputResult result = router.route(context(
            item(Material.WHEAT, 5),
            item(Material.WHEAT, 7)
        ));

        assertEquals(2, calls.get());
        assertEquals(List.of(3, 5), result.getRemainingItems().stream().map(ItemStack::getAmount).toList());
    }

    @Test
    void preCommitLinkageFailureFailsOpen() {
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            throw new NoClassDefFoundError("dependency missing");
        });

        assertEquals(64, router.route(context(item(Material.WHEAT, 64))).getRemainingItems().getFirst().getAmount());
    }

    @Test
    void postCommitFailureCannotRestoreOriginalFullAmount() {
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            tracker.record(result(context, CollectResult.Status.PARTIAL, 40L));
            throw new AssertionError("after commit");
        });

        assertEquals(24, router.route(context(item(Material.WHEAT, 64))).getRemainingItems().getFirst().getAmount());
    }

    @Test
    void unreadablePostCommitFailureCannotEscapeOrRestoreOriginalAmount() {
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            tracker.record(result(context, CollectResult.Status.PARTIAL, 40L));
            throw new AssertionError() {
                @Override
                public String getMessage() {
                    throw new LinkageError("message lookup failed");
                }
            };
        });

        assertEquals(24, router.route(context(item(Material.WHEAT, 64))).getRemainingItems().getFirst().getAmount());
    }

    @Test
    void stoppedRouterRejectsNewRoutingCalls() {
        AtomicInteger calls = new AtomicInteger();
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            calls.incrementAndGet();
            return result(context, CollectResult.Status.COLLECTED, context.itemStack().getAmount());
        });
        router.stopAccepting();

        assertEquals(64, router.route(context(item(Material.WHEAT, 64))).getRemainingItems().getFirst().getAmount());
        assertEquals(0, calls.get());
    }

    @Test
    void inFlightRoutingFinishesWhileShutdownDrains() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CraftionSpawnerOutputRouter router = router((context, tracker) -> {
            entered.countDown();
            try {
                assertTrue(release.await(2L, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            CollectResult result = result(context, CollectResult.Status.COLLECTED, context.itemStack().getAmount());
            tracker.record(result);
            return result;
        });

        CompletableFuture<SpawnerOutputResult> routed = CompletableFuture.supplyAsync(
            () -> router.route(context(item(Material.WHEAT, 64)))
        );
        assertTrue(entered.await(2L, TimeUnit.SECONDS));
        CompletableFuture<Void> drained = router.stopAccepting();
        assertFalse(drained.isDone());

        release.countDown();

        assertTrue(routed.get(2L, TimeUnit.SECONDS).getRemainingItems().isEmpty());
        drained.get(2L, TimeUnit.SECONDS);
        assertTrue(drained.isDone());
    }

    private static CraftionSpawnerOutputRouter router(CraftionSpawnerOutputRouter.Collector collector) {
        CraftionSpawnerOutputRouter router = new CraftionSpawnerOutputRouter(collector, ignored -> {
        });
        router.startAccepting();
        return router;
    }

    private static SpawnerOutputContext context(ItemStack... items) {
        World world = mock(World.class);
        Location location = new Location(world, 5.0D, 64.0D, 9.0D);
        SpawnerDataDTO spawner = new SpawnerDataDTO(
            "spawner-one",
            location,
            EntityType.ZOMBIE,
            null,
            1,
            64,
            1,
            1,
            1,
            100L,
            200L
        );
        return new SpawnerOutputContext(spawner, List.of(items));
    }

    private static CollectResult result(CollectContext context, CollectResult.Status status, long accepted) {
        long requested = context.itemStack().getAmount();
        return new CollectResult(
            status,
            CollectReason.SPAWNER_OUTPUT,
            "farmer-one",
            "region-one",
            WHEAT,
            requested,
            accepted,
            requested - accepted,
            accepted,
            100L
        );
    }
}
