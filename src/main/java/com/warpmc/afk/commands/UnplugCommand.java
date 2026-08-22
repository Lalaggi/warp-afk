package com.warpmc.afk.commands;

import com.warpmc.afk.manager.AFKManager;
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
            sender.sendMessage(Component.text("Only players can use /unplug", NamedTextColor.RED));
            return true;
        }
        if (!manager.isFactoryAvailable()) {
            player.sendMessage(Component.text("WarpAFK: FakePlayer API not available on this server", NamedTextColor.RED));
            return true;
        }
        long minutes = -1;
        String reason = null;
        if (args.length >= 1) {
            try {
                minutes = Long.parseLong(args[0]);
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
            player.sendMessage(Component.text("Failed to go AFK: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }

        String kick = managerReasonKickedMessage();
        // Kick real player — bot stays
        player.kick(Component.text(kick));
        return true;
    }

    private String reasonKickedMessage() {
        return "§6Your player will be AFK§r";
    }

    private String managerReasonKickedMessage() {
        try {
            var plugin = manager.getPlugin();
            String msg = plugin.getConfig().getString("messages.kick-message", reasonKickedMessage());
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg.replace("<gold>", "§6").replace("<r>", "§r"));
        } catch (Exception e) {
            return reasonKickedMessage();
        }
    }
}
