package com.craftion.farmer.module;

import com.craftion.farmer.farmer.Farmer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public final class AutoKillDeathTracker {

    private final ConcurrentMap<UUID, AutoKillDeathContext> contexts = new ConcurrentHashMap<>();

    public void mark(LivingEntity entity, Farmer farmer) {
        if (entity == null || farmer == null) {
            return;
        }
        this.contexts.put(entity.getUniqueId(), new AutoKillDeathContext(farmer, farmer.regionId(), entity.getType()));
    }

    public Optional<AutoKillDeathContext> context(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.contexts.get(entity.getUniqueId()));
    }

    public void unmark(Entity entity) {
        if (entity != null) {
            this.contexts.remove(entity.getUniqueId());
        }
    }
}
