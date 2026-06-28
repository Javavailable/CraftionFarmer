package com.craftion.farmer.gui.listener;

import com.craftion.farmer.gui.MenuAction;
import com.craftion.farmer.gui.MenuContext;
import com.craftion.farmer.gui.MenuHolder;
import com.craftion.farmer.gui.MenuService;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class MenuClickListener implements Listener {

    private final MenuService menuService;

    public MenuClickListener(MenuService menuService) {
        this.menuService = menuService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder inventoryHolder = topInventory.getHolder();
        if (!(inventoryHolder instanceof MenuHolder menuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!isTopInventoryClick(event, topInventory)) {
            return;
        }
        if (!isSafeActionClick(event.getClick())) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || currentItem.getType().isAir()) {
            return;
        }

        int slot = event.getRawSlot();
        Optional<MenuAction> action = this.menuService.actionRegistry().resolve(menuHolder, slot, event.getClick());
        action.ifPresent(value -> this.menuService.execute(new MenuContext(player, menuHolder, slot, event.getClick()), value));
    }

    private boolean isTopInventoryClick(InventoryClickEvent event, Inventory topInventory) {
        Inventory clickedInventory = event.getClickedInventory();
        return clickedInventory != null && clickedInventory.equals(topInventory) && event.getRawSlot() >= 0 && event.getRawSlot() < topInventory.getSize();
    }

    private boolean isSafeActionClick(ClickType clickType) {
        return clickType == ClickType.LEFT || clickType == ClickType.RIGHT;
    }
}
