package com.craftion.farmer.collect.listener;

import com.craftion.farmer.collect.CollectContext;
import com.craftion.farmer.collect.CollectResult;
import com.craftion.farmer.collect.CollectService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

public final class CollectItemSpawnListener implements Listener {

    private final CollectService collectService;

    public CollectItemSpawnListener(CollectService collectService) {
        this.collectService = collectService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        CollectResult result = this.collectService.collect(CollectContext.itemSpawn(event.getEntity()));
        if (result.fullyCollected()) {
            event.setCancelled(true);
            return;
        }

        if (result.partiallyCollected()) {
            ItemStack itemStack = event.getEntity().getItemStack().clone();
            itemStack.setAmount((int) result.remainingAmount());
            event.getEntity().setItemStack(itemStack);
        }
    }
}
