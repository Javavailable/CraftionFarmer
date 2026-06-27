package com.craftion.farmer.scheduler;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

public final class PaperSchedulerAdapter implements SchedulerAdapter {

    private final JavaPlugin plugin;
    private final Set<ScheduledTaskHandle> tasks = ConcurrentHashMap.newKeySet();

    public PaperSchedulerAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String type() {
        return "Paper";
    }

    @Override
    public ScheduledTaskHandle runAsync(Runnable task) {
        OneShotTask oneShotTask = new OneShotTask(task);
        BukkitTask bukkitTask = scheduler().runTaskAsynchronously(this.plugin, oneShotTask);
        return trackOneShot(bukkitTask, oneShotTask);
    }

    @Override
    public ScheduledTaskHandle runGlobal(Runnable task) {
        OneShotTask oneShotTask = new OneShotTask(task);
        BukkitTask bukkitTask = scheduler().runTask(this.plugin, oneShotTask);
        return trackOneShot(bukkitTask, oneShotTask);
    }

    @Override
    public ScheduledTaskHandle runDelayed(Runnable task, long delayTicks) {
        OneShotTask oneShotTask = new OneShotTask(task);
        BukkitTask bukkitTask = scheduler().runTaskLater(this.plugin, oneShotTask, normalizeDelay(delayTicks));
        return trackOneShot(bukkitTask, oneShotTask);
    }

    @Override
    public ScheduledTaskHandle runRepeating(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = scheduler().runTaskTimer(this.plugin, requireTask(task), normalizeDelay(delayTicks), normalizePeriod(periodTicks));
        return track(bukkitTask);
    }

    @Override
    public ScheduledTaskHandle runAtEntity(Entity entity, Runnable task) {
        Objects.requireNonNull(entity, "entity");
        return runGlobal(task);
    }

    @Override
    public ScheduledTaskHandle runAtLocation(Location location, Runnable task) {
        Objects.requireNonNull(location, "location");
        return runGlobal(task);
    }

    @Override
    public void cancelTasks() {
        for (ScheduledTaskHandle task : Set.copyOf(this.tasks)) {
            task.cancel();
        }
        this.tasks.clear();
        scheduler().cancelTasks(this.plugin);
    }

    private BukkitScheduler scheduler() {
        return this.plugin.getServer().getScheduler();
    }

    private ScheduledTaskHandle trackOneShot(BukkitTask bukkitTask, OneShotTask oneShotTask) {
        ScheduledTaskHandle handle = track(bukkitTask);
        oneShotTask.bind(handle);
        return handle;
    }

    private ScheduledTaskHandle track(BukkitTask bukkitTask) {
        ScheduledTaskHandle handle = new BukkitScheduledTaskHandle(bukkitTask);
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
        return Math.max(0L, delayTicks);
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

    private final class BukkitScheduledTaskHandle implements ScheduledTaskHandle {

        private final BukkitTask task;

        private BukkitScheduledTaskHandle(BukkitTask task) {
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
