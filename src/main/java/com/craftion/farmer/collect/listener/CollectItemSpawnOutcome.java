package com.craftion.farmer.collect.listener;

import com.craftion.farmer.collect.CollectResult;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class CollectItemSpawnOutcome {

    private final boolean cancelEvent;
    private final ItemStack replacementItemStack;

    private CollectItemSpawnOutcome(boolean cancelEvent, ItemStack replacementItemStack) {
        this.cancelEvent = cancelEvent;
        this.replacementItemStack = cloneItemStack(replacementItemStack);
    }

    public static CollectItemSpawnOutcome plan(CollectResult result, ItemStack originalItemStack) {
        Objects.requireNonNull(result, "result");
        if (result.fullyCollected()) {
            return new CollectItemSpawnOutcome(true, null);
        }
        if (result.partiallyCollected()) {
            Objects.requireNonNull(originalItemStack, "originalItemStack");
            ItemStack remainder = originalItemStack.clone();
            remainder.setAmount(Math.toIntExact(result.remainingAmount()));
            return new CollectItemSpawnOutcome(false, remainder);
        }
        return new CollectItemSpawnOutcome(false, null);
    }

    public boolean cancelEvent() {
        return this.cancelEvent;
    }

    public Optional<ItemStack> replacementItemStack() {
        return Optional.ofNullable(cloneItemStack(this.replacementItemStack));
    }

    private static ItemStack cloneItemStack(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }
}
