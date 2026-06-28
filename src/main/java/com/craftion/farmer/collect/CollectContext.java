package com.craftion.farmer.collect;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

public record CollectContext(Item item, ItemStack itemStack, Location location, CollectReason reason) {

    public CollectContext {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(itemStack, "itemStack");
        Objects.requireNonNull(location, "location");
        reason = reason == null ? CollectReason.ITEM_SPAWN : reason;
        itemStack = itemStack.clone();
    }

    public static CollectContext itemSpawn(Item item) {
        Objects.requireNonNull(item, "item");
        return new CollectContext(item, item.getItemStack(), item.getLocation(), CollectReason.ITEM_SPAWN);
    }
}
