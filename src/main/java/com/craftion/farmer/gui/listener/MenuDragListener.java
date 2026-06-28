package com.craftion.farmer.gui.listener;

import com.craftion.farmer.gui.MenuHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class MenuDragListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder inventoryHolder = event.getView().getTopInventory().getHolder();
        if (inventoryHolder instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
