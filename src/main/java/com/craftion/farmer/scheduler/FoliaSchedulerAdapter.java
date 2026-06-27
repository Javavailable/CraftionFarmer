package com.craftion.farmer.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final JavaPlugin plugin;
    private final String type;
    private final Set<ScheduledTaskHandle> tasks = ConcurrentHashMap.newKeySet();

    public FoliaSchedulerAdapter(JavaPlugin plugin, String type) {
        this.plugin = plugin;
        this.type = type;
    }

    @Override
    public String type() {
        return this.type;
    }

    @Override
    public ScheduledTaskHandle runAsync(Runnable task) {
        OneShotTask oneShotTask = new OneShotTask(task);
        ScheduledTask scheduledTask = this.plugin.getServer().getAsyncScheduler().runNow(this.plugin, ignored -> oneShotTask.run());
        return trackOneShot(scheduledTask, oneShotTask);
    }

    @Override
    public ScheduledTaskHandle runGlobal(Runnable task) {
        OneShotTask oneShotTask = new OneShotTask(task);
        ScheduledTask scheduledTask = this.plugin.getServer().getGlobalRegionScheduler().run(this.plugin, ignored -> oneShotTask.run());
        return trackOneShot(scheduledTask, oneShotTask);
    }

    @Override
    public ScheduledTaskHandle runDelayed(Runnable task, long delayTicks) {
        OneShotTask oneShotTask = new OneShotTask(task);
        ScheduledTask scheduledTask = this.plugin.getServer().getGlobalRegionScheduler()
            .runDelayed(this.plugin, ignored -> oneShotTask.run(), normalizeDelay(delayTicks));
        return trackOneShot(scheduledTask, oneShotTask);
    }

    @Override
    public ScheduledTaskHandle runRepeating(Runnable task, long delayTicks, long periodTicks) {
        Runnable repeatingTask = requireTask(task);
        ScheduledTask scheduledTask = this.plugin.getServer().getGlobalRegionScheduler()
            .runAtFixedRate(this.plugin, ignored -> repeatingTask.run(), normalizeDelay(delayTicks), normalizePeriod(periodTicks));
        return track(scheduledTask);
    }

    @Override
    public ScheduledTaskHandle runAtEntity(Entity entity, Runnable task) {
        Objects.requireNonNull(entity, "entity");

        OneShotTask oneShotTask = new OneShotTask(task);
        ScheduledTask scheduledTask = entity.getScheduler().run(this.plugin, ignored -> oneShotTask.run(), oneShotTask::finish);
        if (scheduledTask == null) {
            return ScheduledTaskHandle.cancelled();
        }

        return trackOneShot(scheduledTask, oneShotTask);
    }

    @Override
    public ScheduledTaskHandle runAtLocation(Location location, Runnable task) {
        Objects.requireNonNull(location, "location");

        OneShotTask oneShotTask = new OneShotTask(task);
        ScheduledTask scheduledTask = this.plugin.getServer().getRegionScheduler().run(this.plugin, location, ignored -> oneShotTask.run());
        return trackOneShot(scheduledTask, oneShotTask);
    }

    @Override
    public void cancelTasks() {
        for (ScheduledTaskHandle task : Set.copyOf(this.tasks)) {
            task.cancel();
        }
        this.tasks.clear();
        this.plugin.getServer().getGlobalRegionScheduler().cancelTasks(this.plugin);
        this.plugin.getServer().getAsyncScheduler().cancelTasks(this.plugin);
    }

    private ScheduledTaskHandle trackOneShot(ScheduledTask scheduledTask, OneShotTask oneShotTask) {
        ScheduledTaskHandle handle = track(scheduledTask);
        oneShotTask.bind(handle);
        return handle;
    }

    private ScheduledTaskHandle track(ScheduledTask scheduledTask) {
        ScheduledTaskHandle handle = new FoliaScheduledTaskHandle(scheduledTask);
        this.tasks.add(handle);
        return handle;
    }

    private void untrack(ScheduledTaskHandle handle) {
        this.tasks.remove(handle);
    }

    private Runnable requireTask(Runnable task) {
        return Objects.requireNonNull(task, "task");
    }

    private long normalizeDelay(long delayTicks) {
        return Math.max(1L, delayTicks);
    }

    private long normalizePeriod(long periodTicks) {
        return Math.max(1L, periodTicks);
    }

    private final class OneShotTask implements Runnable {

        private final Runnable task;
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private volatile ScheduledTaskHandle handle;

        private OneShotTask(Runnable task) {
            this.task = requireTask(task);
        }

        @Override
        public void run() {
            try {
                this.task.run();
            } finally {
                finish();
            }
        }

        private void bind(ScheduledTaskHandle handle) {
            this.handle = handle;
            if (this.finished.get()) {
                untrack(handle);
            }
        }

        private void finish() {
            if (!this.finished.compareAndSet(false, true)) {
                return;
            }

            ScheduledTaskHandle currentHandle = this.handle;
            if (currentHandle != null) {
                untrack(currentHandle);
            }
        }
    }

    private final class FoliaScheduledTaskHandle implements ScheduledTaskHandle {

        private final ScheduledTask task;

        private FoliaScheduledTaskHandle(ScheduledTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (!this.task.isCancelled()) {
                this.task.cancel();
            }
            untrack(this);
        }

        @Override
        public boolean isCancelled() {
            return this.task.isCancelled();
        }
    }
}
