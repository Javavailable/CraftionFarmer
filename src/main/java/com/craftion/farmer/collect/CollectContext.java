package com.craftion.farmer.collect;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

public record CollectContext(ItemStack itemStack, Location location, CollectReason reason, boolean playerDrop) {

    public CollectContext {
        itemStack = cloneItemStack(itemStack);
        location = cloneLocation(location);
        reason = reason == null ? CollectReason.ITEM_SPAWN : reason;
    }

    @Override
    public ItemStack itemStack() {
        return cloneItemStack(this.itemStack);
    }

    @Override
    public Location location() {
        return cloneLocation(this.location);
    }

    public static CollectContext itemSpawn(Item item) {
        Objects.requireNonNull(item, "item");
        return new CollectContext(
            item.getItemStack(),
            item.getLocation(),
            CollectReason.ITEM_SPAWN,
            item.getThrower() != null
        );
    }

    private static ItemStack cloneItemStack(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }
}
