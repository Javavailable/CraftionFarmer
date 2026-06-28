package com.craftion.farmer.collect;

import com.craftion.farmer.farmer.MaterialKey;

public record CollectResult(
    Status status,
    CollectReason reason,
    String farmerId,
    String regionId,
    MaterialKey materialKey,
    long requestedAmount,
    long collectedAmount,
    long remainingAmount,
    long storageAmount,
    long capacity
) {

    public CollectResult {
        if (status == null) {
            throw new IllegalArgumentException("Collect status cannot be null.");
        }
        reason = reason == null ? CollectReason.ITEM_SPAWN : reason;
        requestedAmount = Math.max(0L, requestedAmount);
        collectedAmount = Math.max(0L, collectedAmount);
        remainingAmount = Math.max(0L, remainingAmount);
        storageAmount = Math.max(0L, storageAmount);
    }

    public static CollectResult skipped(Status status, CollectContext context) {
        return new CollectResult(status, context == null ? CollectReason.ITEM_SPAWN : context.reason(), null, null, null, amount(context), 0L, amount(context), 0L, -1L);
    }

    public static CollectResult skipped(Status status, CollectContext context, String regionId) {
        return new CollectResult(status, context == null ? CollectReason.ITEM_SPAWN : context.reason(), null, regionId, null, amount(context), 0L, amount(context), 0L, -1L);
    }

    public static CollectResult collected(
        Status status,
        CollectContext context,
        String farmerId,
        String regionId,
        MaterialKey materialKey,
        long collectedAmount,
        long remainingAmount,
        long storageAmount,
        long capacity
    ) {
        return new CollectResult(status, context.reason(), farmerId, regionId, materialKey, amount(context), collectedAmount, remainingAmount, storageAmount, capacity);
    }

    public boolean changedStorage() {
        return this.collectedAmount > 0L;
    }

    public boolean fullyCollected() {
        return this.changedStorage() && this.remainingAmount <= 0L;
    }

    public boolean partiallyCollected() {
        return this.changedStorage() && this.remainingAmount > 0L;
    }

    private static long amount(CollectContext context) {
        if (context == null || context.itemStack() == null) {
            return 0L;
        }
        return Math.max(0, context.itemStack().getAmount());
    }

    public enum Status {
        COLLECTED,
        PARTIAL,
        STORAGE_FULL,
        DISABLED,
        NOT_SKYBLOCK_WORLD,
        NO_REGION,
        NO_FARMER,
        FARMER_DISABLED,
        PRODUCT_DISABLED,
        MATERIAL_NOT_ALLOWED,
        ITEM_HAS_META,
        PLAYER_DROP,
        EMPTY_ITEM,
        INVALID_ITEM
    }
}
