package com.warpmc.afk.commands;

import com.warpmc.afk.manager.AFKManager;
import com.warpmc.afk.util.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class UnplugCommand implements CommandExecutor {

    private final AFKManager manager;

    public UnplugCommand(AFKManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /" + label, NamedTextColor.RED));
            return true;
        }
        // Respect enable toggle similar to upstream enableUnplugCommand / enableAfkCommand
        boolean isAfkAlias = label.equalsIgnoreCase("afk");
        if (isAfkAlias && !manager.getPlugin().getConfig().getBoolean("commands.enable-afk", true)) {
            sender.sendMessage(Component.text("Command /afk is disabled in config", NamedTextColor.RED));
            return true;
        }
        if (!isAfkAlias && !manager.getPlugin().getConfig().getBoolean("commands.enable-unplug", true)) {
            sender.sendMessage(Component.text("Command /unplug is disabled in config", NamedTextColor.RED));
            return true;
        }
        if (!manager.isFactoryAvailable()) {
            player.sendMessage(Component.text("WarpAFK: FakePlayer API not available on this server", NamedTextColor.RED));
            return true;
        }
        if (!manager.getPlugin().getConfig().getBoolean("main.enabled", true)) {
            player.sendMessage(Component.text("WarpAFK is currently disabled (main.enabled=false)", NamedTextColor.RED));
            return true;
        }
        long minutes = -1;
        String reason = null;
        if (args.length >= 1) {
            try {
                minutes = DurationParser.parseMinutes(args[0]);
                if (args.length >= 2) {
                    reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                }
            } catch (NumberFormatException e) {
                // first arg is reason, use default timeout
                minutes = manager.getDefaultTimeout();
                reason = String.join(" ", args);
            }
        } else {
            minutes = manager.getDefaultTimeout();
        }

        try {
            manager.spawnForPlayer(player, minutes, reason);
        } catch (Exception e) {
            // If spawn failed after removing real (duplicate handling), player may already be offline
            if (player.isOnline()) {
                player.sendMessage(Component.text("Failed to go AFK: " + e.getMessage(), NamedTextColor.RED));
            }
            manager.getPlugin().getLogger().warning("spawnForPlayer failed for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return true;
        }

        String kick = managerReasonKickedMessage();
        // Kick real player — bot stays. Builder/PlayerList may have already removed real to avoid
        // duplicate UUID Force-added path; only kick if still online.
        try {
            if (player.isOnline()) {
                // Ensure we kick the real player, not the fake (same UUID). Check that Bukkit player is not already a FakePlayer
                if (!(player instanceof org.purpurmc.purpur.player.FakePlayer)) {
                    player.kick(Component.text(kick));
                }
            }
        } catch (Exception ignored) {
            // Player already disconnected by server's real->fake handling — ignore
        }
        return true;
    }

    private String reasonKickedMessage() {
        return "§6Your player will be AFK§r";
    }

    private String managerReasonKickedMessage() {
        try {
            var plugin = manager.getPlugin();
            String msg = plugin.getConfig().getString("messages.kick-message", reasonKickedMessage());
            if (msg == null) return reasonKickedMessage();
            // Render MiniMessage first, then fallback to legacy color codes
            try {
                return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg).toString();
            } catch (Exception ignored) {
                // If MiniMessage fails, try legacy & codes
                return org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    msg.replace("<gold>", "§6").replace("<r>", "§r"));
            }
        } catch (Exception e) {
            return reasonKickedMessage();
        }
    }
}
