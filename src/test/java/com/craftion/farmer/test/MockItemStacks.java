package com.craftion.farmer.test;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MockItemStacks {

    private MockItemStacks() {
    }

    public static ItemStack item(Material material, int amount) {
        return item(material, amount, false);
    }

    public static ItemStack item(Material material, int amount, boolean hasMeta) {
        ItemMeta itemMeta = hasMeta ? mock(ItemMeta.class) : null;
        return item(material, amount, itemMeta);
    }

    private static ItemStack item(Material material, int amount, ItemMeta itemMeta) {
        AtomicInteger mutableAmount = new AtomicInteger(amount);
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(material);
        when(itemStack.getAmount()).thenAnswer(ignored -> mutableAmount.get());
        doAnswer(invocation -> {
            mutableAmount.set(invocation.getArgument(0));
            return null;
        }).when(itemStack).setAmount(org.mockito.ArgumentMatchers.anyInt());
        when(itemStack.hasItemMeta()).thenReturn(itemMeta != null);
        when(itemStack.getItemMeta()).thenReturn(itemMeta);
        when(itemStack.clone()).thenAnswer(ignored -> item(material, mutableAmount.get(), itemMeta));
        return itemStack;
    }
}
