package com.craftion.farmer.collect;

import static com.craftion.farmer.test.MockItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class CollectContextTest {

    @Test
    void itemStackIsDefensivelyClonedOnConstructionAndAccess() {
        ItemStack original = item(Material.WHEAT, 12);
        CollectContext context = new CollectContext(original, location(), CollectReason.MODULE, false);

        original.setAmount(3);
        ItemStack firstAccess = context.itemStack();
        firstAccess.setAmount(1);
        ItemStack secondAccess = context.itemStack();

        assertEquals(12, secondAccess.getAmount());
        assertNotSame(original, firstAccess);
        assertNotSame(firstAccess, secondAccess);
    }

    @Test
    void locationIsDefensivelySnapshottedOnConstructionAndAccess() {
        Location original = location();
        CollectContext context = new CollectContext(item(Material.WHEAT, 1), original, CollectReason.MODULE, false);

        original.setX(99.0D);
        Location firstAccess = context.location();
        firstAccess.setY(1.0D);
        Location secondAccess = context.location();

        assertEquals(4.0D, secondAccess.getX());
        assertEquals(64.0D, secondAccess.getY());
        assertNotSame(original, firstAccess);
        assertNotSame(firstAccess, secondAccess);
    }

    @Test
    void nullReasonDefaultsAndPlayerDropFlagIsPreserved() {
        CollectContext context = new CollectContext(item(Material.WHEAT, 1), location(), null, true);

        assertEquals(CollectReason.ITEM_SPAWN, context.reason());
        assertTrue(context.playerDrop());
    }

    @Test
    void itemSpawnFactoryExtractsEntityDataAndThrower() {
        ItemStack stack = item(Material.WHEAT, 7);
        Location location = location();
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(stack);
        when(entity.getLocation()).thenReturn(location);
        when(entity.getThrower()).thenReturn(UUID.randomUUID());

        CollectContext context = CollectContext.itemSpawn(entity);
        stack.setAmount(2);
        location.setZ(100.0D);

        assertEquals(7, context.itemStack().getAmount());
        assertEquals(8.0D, context.location().getZ());
        assertEquals(CollectReason.ITEM_SPAWN, context.reason());
        assertTrue(context.playerDrop());
    }

    @Test
    void itemSpawnFactoryPreservesNonPlayerDropSource() {
        Item entity = mock(Item.class);
        ItemStack stack = item(Material.WHEAT, 1);
        Location location = location();
        when(entity.getItemStack()).thenReturn(stack);
        when(entity.getLocation()).thenReturn(location);
        when(entity.getThrower()).thenReturn(null);

        assertFalse(CollectContext.itemSpawn(entity).playerDrop());
    }

    private static Location location() {
        return new Location(mock(World.class), 4.0D, 64.0D, 8.0D);
    }
}
