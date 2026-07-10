package com.craftion.farmer.collect.listener;

import static com.craftion.farmer.test.MockItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftion.farmer.collect.CollectReason;
import com.craftion.farmer.collect.CollectResult;
import com.craftion.farmer.farmer.MaterialKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class CollectItemSpawnOutcomeTest {

    private static final MaterialKey WHEAT = MaterialKey.of("WHEAT");

    @Test
    void fullyCollectedPlansCancellationWithoutReplacement() {
        CollectItemSpawnOutcome outcome = CollectItemSpawnOutcome.plan(result(CollectResult.Status.COLLECTED, 10, 10, 0), item(Material.WHEAT, 10));

        assertTrue(outcome.cancelEvent());
        assertTrue(outcome.replacementItemStack().isEmpty());
    }

    @Test
    void partialPlansExactRemainderAndPreservesMaterialAndMetadata() {
        ItemStack original = item(Material.WHEAT, 10, true);
        ItemMeta originalMeta = original.getItemMeta();

        CollectItemSpawnOutcome outcome = CollectItemSpawnOutcome.plan(result(CollectResult.Status.PARTIAL, 10, 4, 6), original);
        ItemStack remainder = outcome.replacementItemStack().orElseThrow();

        assertFalse(outcome.cancelEvent());
        assertEquals(6, remainder.getAmount());
        assertEquals(Material.WHEAT, remainder.getType());
        assertTrue(remainder.hasItemMeta());
        assertSame(originalMeta, remainder.getItemMeta());
        assertEquals(10, original.getAmount());
    }

    @Test
    void skippedResultLeavesEntityStackUntouched() {
        CollectItemSpawnOutcome outcome = CollectItemSpawnOutcome.plan(result(CollectResult.Status.NO_REGION, 10, 0, 10), item(Material.WHEAT, 10));

        assertFalse(outcome.cancelEvent());
        assertTrue(outcome.replacementItemStack().isEmpty());
    }

    @Test
    void storageFullWithZeroAcceptedLeavesEntityStackUntouched() {
        CollectItemSpawnOutcome outcome = CollectItemSpawnOutcome.plan(result(CollectResult.Status.STORAGE_FULL, 10, 0, 10), item(Material.WHEAT, 10));

        assertFalse(outcome.cancelEvent());
        assertTrue(outcome.replacementItemStack().isEmpty());
    }

    private static CollectResult result(CollectResult.Status status, long requested, long collected, long remaining) {
        return new CollectResult(
            status,
            CollectReason.ITEM_SPAWN,
            "farmer-one",
            "region-one",
            WHEAT,
            requested,
            collected,
            remaining,
            collected,
            100L
        );
    }
}
