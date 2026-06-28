package com.craftion.farmer.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;

public final class FarmerPaperCommand implements BasicCommand {

    private final FarmerCommand delegate;

    public FarmerPaperCommand(FarmerCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        this.delegate.onCommand(source.getSender(), org.bukkit.Bukkit.getPluginCommand("help"), "farmer", args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        List<String> suggestions = this.delegate.onTabComplete(source.getSender(), org.bukkit.Bukkit.getPluginCommand("help"), "farmer", args);
        return suggestions == null ? List.of() : suggestions;
    }
}
