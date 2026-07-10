package com.craftion.farmer.collect;

import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.output.SpawnerOutputContext;
import github.nighter.smartspawner.api.output.SpawnerOutputResult;
import github.nighter.smartspawner.api.output.SpawnerOutputRouter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Routes CraftionSpawner item output through the shared Farmer collection path. */
public final class CraftionSpawnerOutputRouter implements SpawnerOutputRouter {

    private final Collector collector;
    private final Consumer<String> failureLogger;
    private final Object lifecycleLock = new Object();
    private boolean accepting;
    private int inFlight;
    private CompletableFuture<Void> drained = CompletableFuture.completedFuture(null);

    public CraftionSpawnerOutputRouter(CollectService collectService, Consumer<String> failureLogger) {
        this(Objects.requireNonNull(collectService, "collectService")::collect, failureLogger);
    }

    CraftionSpawnerOutputRouter(Collector collector, Consumer<String> failureLogger) {
        this.collector = Objects.requireNonNull(collector, "collector");
        this.failureLogger = Objects.requireNonNull(failureLogger, "failureLogger");
    }

    public void startAccepting() {
        synchronized (this.lifecycleLock) {
            if (this.accepting) {
                return;
            }
            if (this.inFlight != 0) {
                throw new IllegalStateException("Cannot restart a draining CraftionSpawner router.");
            }
            this.drained = new CompletableFuture<>();
            this.accepting = true;
        }
    }

    public CompletableFuture<Void> stopAccepting() {
        synchronized (this.lifecycleLock) {
            this.accepting = false;
            if (this.inFlight == 0) {
                this.drained.complete(null);
            }
            return this.drained;
        }
    }

    @Override
    public SpawnerOutputResult route(SpawnerOutputContext context) {
        SpawnerOutputResult failOpen = SpawnerOutputResult.passThrough(context);
        if (!enter()) {
            return failOpen;
        }

        RouteState state = null;
        try {
            state = preflight(context, failOpen);
            if (state == null) {
                return failOpen;
            }

            for (int index = 0; index < state.size(); index++) {
                routeOne(state, index);
            }
            return state.result();
        } catch (Throwable failure) {
            logFailure("routing failed: " + safeDescription(failure));
            return state == null ? failOpen : state.resultAfterFailure();
        } finally {
            exit();
        }
    }

    private RouteState preflight(SpawnerOutputContext context, SpawnerOutputResult failOpen) {
        SpawnerDataDTO spawner = context.getSpawner();
        if (spawner == null) {
            return null;
        }
        Location location = spawner.getLocation();
        if (location == null || location.getWorld() == null) {
            return null;
        }

        List<ItemStack> generatedItems = context.getGeneratedItems();
        if (generatedItems == null || generatedItems.isEmpty()) {
            return null;
        }

        List<ItemStack> snapshots = new ArrayList<>(generatedItems.size());
        for (ItemStack itemStack : generatedItems) {
            if (itemStack == null || itemStack.getAmount() <= 0 || isAir(itemStack.getType())) {
                return null;
            }
            snapshots.add(itemStack.clone());
        }
        return new RouteState(location, snapshots, failOpen);
    }

    private void routeOne(RouteState state, int index) {
        ItemStack itemStack = state.item(index);
        CollectContext collectContext = new CollectContext(
            itemStack,
            state.location(),
            CollectReason.SPAWNER_OUTPUT,
            false
        );
        CollectCommitTracker tracker = new CollectCommitTracker();

        CollectResult result;
        try {
            result = this.collector.collect(collectContext, tracker);
        } catch (Throwable failure) {
            result = tracker.committedResult().orElse(null);
            if (result == null) {
                logFailure("pre-commit collection failed open: " + safeDescription(failure));
                return;
            }
            state.recordAccepted(index, result);
            logFailure("post-commit collection failure was isolated: " + safeDescription(failure));
            return;
        }
        state.recordAccepted(index, result);
    }

    private boolean enter() {
        synchronized (this.lifecycleLock) {
            if (!this.accepting) {
                return false;
            }
            this.inFlight++;
            return true;
        }
    }

    private void exit() {
        synchronized (this.lifecycleLock) {
            this.inFlight--;
            if (!this.accepting && this.inFlight == 0) {
                this.drained.complete(null);
            }
        }
    }

    private void logFailure(String message) {
        try {
            this.failureLogger.accept(message);
        } catch (Throwable ignored) {
            // Diagnostics must never reopen CraftionSpawner's fail-open duplication window.
        }
    }

    private static String safeDescription(Throwable throwable) {
        try {
            String message = throwable.getMessage();
            return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
        } catch (Throwable ignored) {
            return "unreadable failure";
        }
    }

    private static boolean isAir(Material material) {
        return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    @FunctionalInterface
    interface Collector {
        CollectResult collect(CollectContext context, CollectCommitTracker tracker);
    }

    private static final class RouteState {

        private final Location location;
        private final List<ItemStack> items;
        private final int[] originalAmounts;
        private final int[] acceptedAmounts;
        private final SpawnerOutputResult failOpen;
        private long acceptedTotal;

        private RouteState(Location location, List<ItemStack> items, SpawnerOutputResult failOpen) {
            this.location = location.clone();
            this.items = List.copyOf(items);
            this.originalAmounts = new int[items.size()];
            for (int index = 0; index < items.size(); index++) {
                this.originalAmounts[index] = items.get(index).getAmount();
            }
            this.acceptedAmounts = new int[items.size()];
            this.failOpen = failOpen;
        }

        private int size() {
            return this.items.size();
        }

        private ItemStack item(int index) {
            return this.items.get(index).clone();
        }

        private Location location() {
            return this.location.clone();
        }

        private void recordAccepted(int index, CollectResult result) {
            if (result == null) {
                return;
            }
            int requestedAmount = this.originalAmounts[index];
            long reportedAccepted = result.collectedAmount();
            int acceptedAmount = (int) Math.min(requestedAmount, Math.max(0L, reportedAccepted));
            int previousAccepted = this.acceptedAmounts[index];
            this.acceptedAmounts[index] = Math.max(previousAccepted, acceptedAmount);
            this.acceptedTotal += this.acceptedAmounts[index] - previousAccepted;
        }

        private SpawnerOutputResult result() {
            if (this.acceptedTotal <= 0L) {
                return this.failOpen;
            }
            return buildAcceptedResult();
        }

        private SpawnerOutputResult resultAfterFailure() {
            if (this.acceptedTotal <= 0L) {
                return this.failOpen;
            }
            try {
                return buildAcceptedResult();
            } catch (Throwable ignored) {
                // Losing a catastrophic remainder is safer than duplicating an already committed deposit.
                return SpawnerOutputResult.consumeAll();
            }
        }

        private SpawnerOutputResult buildAcceptedResult() {
            List<ItemStack> remaining = new ArrayList<>(this.items.size());
            for (int index = 0; index < this.items.size(); index++) {
                ItemStack original = this.items.get(index);
                int remainderAmount = this.originalAmounts[index] - this.acceptedAmounts[index];
                if (remainderAmount <= 0) {
                    continue;
                }
                ItemStack remainder = original.clone();
                remainder.setAmount(remainderAmount);
                remaining.add(remainder);
            }
            return remaining.isEmpty()
                ? SpawnerOutputResult.consumeAll()
                : SpawnerOutputResult.remaining(remaining);
        }
    }
}
