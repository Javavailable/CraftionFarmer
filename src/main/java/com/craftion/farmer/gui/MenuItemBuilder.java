package com.craftion.farmer.gui;

import com.craftion.farmer.util.TextUtil;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MenuItemBuilder {

    private final Material material;
    private int amount = 1;
    private String displayName;
    private List<String> lore = List.of();

    private MenuItemBuilder(Material material) {
        this.material = Objects.requireNonNull(material, "material");
    }

    public static MenuItemBuilder of(Material material) {
        return new MenuItemBuilder(material);
    }

    public MenuItemBuilder amount(int amount) {
        this.amount = Math.max(1, amount);
        return this;
    }

    public MenuItemBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public MenuItemBuilder lore(List<String> lore) {
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        return this;
    }

    public ItemStack build() {
        ItemStack itemStack = new ItemStack(this.material, this.amount);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }

        if (this.displayName != null && !this.displayName.isBlank()) {
            itemMeta.displayName(TextUtil.parse(this.displayName));
        }
        if (!this.lore.isEmpty()) {
            itemMeta.lore(this.lore.stream().filter(line -> line != null && !line.isBlank()).map(TextUtil::parse).toList());
        }

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}
