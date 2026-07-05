package com.craftion.farmer.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface SchedulerAdapter {

    String type();

    ScheduledTaskHandle runAsync(Runnable task);

    ScheduledTaskHandle runGlobal(Runnable task);

    ScheduledTaskHandle runDelayed(Runnable task, long delayTicks);

    ScheduledTaskHandle runRepeating(Runnable task, long delayTicks, long periodTicks);

    default ScheduledTaskHandle runAtEntity(Entity entity, Runnable task) {
        return runAtEntity(entity, task, () -> {
        });
    }

    ScheduledTaskHandle runAtEntity(Entity entity, Runnable task, Runnable retiredTask);

    ScheduledTaskHandle runAtLocation(Location location, Runnable task);

    void cancelTasks();
}
