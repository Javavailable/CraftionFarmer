package com.craftion.farmer.gui;

import com.craftion.farmer.farmer.FarmerMember;
import com.craftion.farmer.farmer.FarmerRole;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

public final class MembersMenu implements FarmerMenu {

    @Override
    public String id() {
        return "members";
    }

    @Override
    public FarmerMenuAccess requiredAccess() {
        return FarmerMenuAccess.MEMBER;
    }

    @Override
    public void render(MenuRenderContext context, MenuLayoutBuilder builder) {
        ConfigurationSection memberSection = context.menuSection().getConfigurationSection("member-items");
        if (memberSection == null) {
            return;
        }

        java.util.List<Integer> slots = memberSection.getIntegerList("slots");
        ConfigurationSection template = memberSection.getConfigurationSection("item");
        if (slots.isEmpty() || template == null) {
            return;
        }

        Map<UUID, FarmerRole> members = new LinkedHashMap<>();
        members.put(context.farmer().ownerUuid(), FarmerRole.OWNER);
        context.farmer().members().values().stream()
            .sorted(Comparator.comparing(member -> member.playerUuid().toString()))
            .forEach(member -> members.putIfAbsent(member.playerUuid(), member.role()));

        int index = 0;
        for (Map.Entry<UUID, FarmerRole> entry : members.entrySet()) {
            if (index >= slots.size()) {
                break;
            }
            builder.putConfiguredItem(slots.get(index), template, context.withPlaceholders(Map.of(
                "player", context.playerName(entry.getKey()),
                "role", context.roleName(entry.getValue())
            )), "PLAYER_HEAD");
            index++;
        }
    }
}
