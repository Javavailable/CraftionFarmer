package com.craftion.farmer.command;

import com.craftion.farmer.CraftionFarmerPlugin;
import com.craftion.farmer.message.MessageService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FarmerCommand implements CommandExecutor, TabCompleter {

    private static final String RELOAD_PERMISSION = "craftionfarmer.admin.reload";

    private final CraftionFarmerPlugin plugin;
    private final MessageService messageService;

    public FarmerCommand(CraftionFarmerPlugin plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            this.messageService.sendList(sender, "commands.farmer.help");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }

        this.messageService.sendList(sender, "commands.farmer.help");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        addIfMatches(completions, "help", args[0]);
        if (sender.hasPermission(RELOAD_PERMISSION)) {
            addIfMatches(completions, "reload", args[0]);
        }
        return completions;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            this.messageService.send(sender, "commands.farmer.no-permission");
            return;
        }

        this.plugin.reloadPluginFiles();
        this.messageService.send(sender, "commands.farmer.reload-success");
    }

    private void addIfMatches(List<String> completions, String value, String input) {
        if (value.startsWith(input.toLowerCase(Locale.ROOT))) {
            completions.add(value);
        }
    }
}
