package com.craftion.farmer.collect.event;

import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.MaterialKey;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class FarmerStorageFullEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Farmer farmer;
    private final MaterialKey materialKey;
    private final long requestedAmount;
    private final long storageAmount;
    private final long capacity;

    public FarmerStorageFullEvent(Farmer farmer, MaterialKey materialKey, long requestedAmount, long storageAmount, long capacity) {
        this.farmer = farmer;
        this.materialKey = materialKey;
        this.requestedAmount = Math.max(0L, requestedAmount);
        this.storageAmount = Math.max(0L, storageAmount);
        this.capacity = capacity;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Farmer farmer() {
        return this.farmer;
    }

    public MaterialKey materialKey() {
        return this.materialKey;
    }

    public long requestedAmount() {
        return this.requestedAmount;
    }

    public long storageAmount() {
        return this.storageAmount;
    }

    public long capacity() {
        return this.capacity;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
