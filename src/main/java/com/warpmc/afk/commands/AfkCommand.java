package com.warpmc.afk.commands;

import com.warpmc.afk.manager.AFKManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class AfkCommand implements CommandExecutor {
    private final UnplugCommand delegate;
    public AfkCommand(AFKManager manager) {
        this.delegate = new UnplugCommand(manager);
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return delegate.onCommand(sender, command, label, args);
    }
}
