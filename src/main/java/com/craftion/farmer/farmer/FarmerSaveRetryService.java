package com.craftion.farmer.farmer;

import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.scheduler.ScheduledTaskHandle;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.plugin.java.JavaPlugin;

public final class FarmerSaveRetryService {

    private static final long RETRY_DELAY_TICKS = 100L;

    private final JavaPlugin plugin;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final FarmerPersistenceService farmerPersistenceService;
    private final ConcurrentMap<String, DirtyFarmer> dirtyFarmers = new ConcurrentHashMap<>();
    private final AtomicBoolean retryRunning = new AtomicBoolean(false);
    private final Object taskLock = new Object();
    private volatile ScheduledTaskHandle retryTask = ScheduledTaskHandle.cancelled();
    private volatile CompletableFuture<Void> activeRetry = CompletableFuture.completedFuture(null);

    public FarmerSaveRetryService(
        JavaPlugin plugin,
        SchedulerAdapter schedulerAdapter,
        DebugLogger debugLogger,
        FarmerPersistenceService farmerPersistenceService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.schedulerAdapter = Objects.requireNonNull(schedulerAdapter, "schedulerAdapter");
        this.debugLogger = Objects.requireNonNull(debugLogger, "debugLogger");
        this.farmerPersistenceService = Objects.requireNonNull(farmerPersistenceService, "farmerPersistenceService");
    }

    public void markDirty(Farmer farmer, String reason) {
        if (farmer == null) {
            return;
        }

        String normalizedReason = reason == null || reason.isBlank() ? "unspecified" : reason.trim();
        this.dirtyFarmers.compute(farmer.farmerId(), (farmerId, current) -> new DirtyFarmer(
            farmer,
            normalizedReason,
            current == null ? 0 : current.attempts()
        ));
        this.debugLogger.debug("Retry scheduled for farmer save: farmer=" + farmer.farmerId() + " reason=" + normalizedReason);
        scheduleRetry();
    }

    public void flushNow() {
        if (!this.retryRunning.compareAndSet(false, true)) {
            scheduleRetry();
            return;
        }

        List<DirtyFarmer> entries = drainDirtyFarmers();
        if (entries.isEmpty()) {
            this.retryRunning.set(false);
            return;
        }

        CompletableFuture<Void> retryFuture;
        try {
            retryFuture = this.farmerPersistenceService.saveAll(farmers(entries));
        } catch (RuntimeException exception) {
            requeue(entries, exception, true);
            this.retryRunning.set(false);
            return;
        }

        this.activeRetry = retryFuture;
        retryFuture.whenComplete((ignored, throwable) -> {
            try {
                if (throwable != null) {
                    requeue(entries, throwable, true);
                    return;
                }
                this.debugLogger.debug("Retry succeeded for dirty farmer saves: count=" + entries.size());
            } finally {
                this.retryRunning.set(false);
                if (!this.dirtyFarmers.isEmpty()) {
                    scheduleRetry();
                }
            }
        });
    }

    public boolean flushNowBlocking(Duration timeout) {
        Duration safeTimeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(5L) : timeout;
        cancelScheduledRetry();
        waitForActiveRetry(safeTimeout);

        List<DirtyFarmer> entries = drainDirtyFarmers();
        if (entries.isEmpty()) {
            return true;
        }

        try {
            this.farmerPersistenceService.saveAll(farmers(entries)).get(safeTimeout.toMillis(), TimeUnit.MILLISECONDS);
            this.debugLogger.debug("Blocking retry flush succeeded: count=" + entries.size());
            return true;
        } catch (Exception exception) {
            requeue(entries, exception, false);
            this.plugin.getLogger().warning("Dirty farmer save flush failed on shutdown: " + readableMessage(exception));
            return false;
        }
    }

    public int pendingCount() {
        return this.dirtyFarmers.size();
    }

    private void scheduleRetry() {
        if (this.dirtyFarmers.isEmpty()) {
            return;
        }
        synchronized (this.taskLock) {
            if (this.retryTask != null && !this.retryTask.isCancelled()) {
                return;
            }
            this.retryTask = this.schedulerAdapter.runDelayed(() -> {
                synchronized (this.taskLock) {
                    this.retryTask = ScheduledTaskHandle.cancelled();
                }
                flushNow();
            }, RETRY_DELAY_TICKS);
        }
    }

    private void cancelScheduledRetry() {
        synchronized (this.taskLock) {
            if (this.retryTask != null && !this.retryTask.isCancelled()) {
                this.retryTask.cancel();
            }
            this.retryTask = ScheduledTaskHandle.cancelled();
        }
    }

    private void waitForActiveRetry(Duration timeout) {
        CompletableFuture<Void> retry = this.activeRetry;
        if (retry == null || retry.isDone()) {
            return;
        }
        try {
            retry.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            this.debugLogger.debug("Active farmer save retry still pending during shutdown: " + readableMessage(exception));
        }
    }

    private List<DirtyFarmer> drainDirtyFarmers() {
        List<DirtyFarmer> entries = List.copyOf(this.dirtyFarmers.values());
        for (DirtyFarmer entry : entries) {
            this.dirtyFarmers.remove(entry.farmer().farmerId(), entry);
        }
        return entries;
    }

    private List<Farmer> farmers(List<DirtyFarmer> entries) {
        List<Farmer> farmers = new ArrayList<>(entries.size());
        for (DirtyFarmer entry : entries) {
            farmers.add(entry.farmer());
        }
        return farmers;
    }

    private void requeue(List<DirtyFarmer> entries, Throwable throwable, boolean schedule) {
        for (DirtyFarmer entry : entries) {
            DirtyFarmer requeued = entry.nextAttempt();
            this.dirtyFarmers.put(entry.farmer().farmerId(), requeued);
            this.plugin.getLogger().warning(
                "Retry failed and requeued farmer save: farmer=" + entry.farmer().farmerId()
                    + " reason=" + entry.reason()
                    + " attempt=" + requeued.attempts()
                    + " error=" + readableMessage(throwable)
            );
        }
        if (schedule) {
            scheduleRetry();
        }
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable == null ? null : (throwable.getCause() == null ? throwable : throwable.getCause());
        if (cause == null) {
            return "unknown";
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }

    private record DirtyFarmer(Farmer farmer, String reason, int attempts) {

        private DirtyFarmer nextAttempt() {
            return new DirtyFarmer(this.farmer, this.reason, this.attempts + 1);
        }
    }
}
