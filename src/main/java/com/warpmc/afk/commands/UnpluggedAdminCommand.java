package com.warpmc.afk.commands;

import com.warpmc.afk.manager.AFKManager;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.purpurmc.purpur.player.FakePlayer;

public class UnpluggedAdminCommand implements CommandExecutor {

    private final AFKManager manager;

    public UnpluggedAdminCommand(AFKManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("WarpAFK — /unplugged-admin <save|reload|purge|spawn|kick|info|list>", NamedTextColor.GOLD));
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "save" -> {
                manager.saveAll();
                sender.sendMessage(Component.text("Saved unplugged.json", NamedTextColor.GREEN));
            }
            case "reload" -> {
                var plugin = (com.warpmc.afk.WarpAFKPlugin) Bukkit.getPluginManager().getPlugin("WarpAFK");
                if (plugin != null) plugin.reloadConfig();
                sender.sendMessage(Component.text("Reloaded config.yml", NamedTextColor.GREEN));
            }
            case "purge" -> {
                manager.purgeAll();
                sender.sendMessage(Component.text("Purged all AFK bots", NamedTextColor.GREEN));
            }
            case "spawn" -> handleSpawn(sender, args);
            case "kick" -> handleKick(sender, args);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender, args);
            case "set" -> handleSet(sender, args);
            default -> sender.sendMessage(Component.text("Unknown subcommand: " + sub, NamedTextColor.RED));
        }
        return true;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /unplugged-admin spawn <player> [minutes] [reason]", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        long minutes = manager.getDefaultTimeout();
        String reason = null;
        if (args.length >= 3) {
            try {
                minutes = Long.parseLong(args[2]);
                if (args.length >= 4) reason = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
            } catch (NumberFormatException e) {
                reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            }
        }
        // Resolve player
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            try {
                manager.spawnForPlayer(online, minutes, reason);
                sender.sendMessage(Component.text("Spawned AFK bot for " + online.getName(), NamedTextColor.GREEN));
            } catch (Exception e) {
                sender.sendMessage(Component.text("Failed: " + e.getMessage(), NamedTextColor.RED));
            }
            return;
        }
        // Offline lookup
        var offline = Bukkit.getOfflinePlayer(targetName);
        UUID uuid;
        String name = targetName;
        World world = null;
        Location loc = null;
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            uuid = offline.getUniqueId();
            name = offline.getName() != null ? offline.getName() : targetName;
            if (sender instanceof Player p) {
                world = p.getWorld();
                loc = p.getLocation();
            } else {
                world = Bukkit.getWorlds().get(0);
                loc = new Location(world, 0, world.getHighestBlockYAt(0, 0) + 1, 0);
            }
        } else {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }
        try {
            manager.spawnForOffline(uuid, name, world, loc, minutes, reason);
            sender.sendMessage(Component.text("Spawned AFK bot for " + name, NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /unplugged-admin kick <player>", NamedTextColor.RED));
            return;
        }
        String target = args[1];
        Player online = Bukkit.getPlayerExact(target);
        UUID uuid = null;
        if (online != null) uuid = online.getUniqueId();
        else {
            var off = Bukkit.getOfflinePlayer(target);
            if (off.hasPlayedBefore()) uuid = off.getUniqueId();
        }
        if (uuid == null) {
            sender.sendMessage(Component.text("Player not found: " + target, NamedTextColor.RED));
            return;
        }
        boolean removed = manager.remove(uuid);
        sender.sendMessage(removed ? Component.text("Kicked AFK bot " + target, NamedTextColor.GREEN) : Component.text("No active AFK bot for " + target, NamedTextColor.YELLOW));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String target = args[1];
            var f = manager.factory();
            if (f == null) { sender.sendMessage(Component.text("Factory unavailable", NamedTextColor.RED)); return; }
            // try UUID parse else name lookup
            FakePlayer fp = null;
            try {
                UUID id = UUID.fromString(target);
                fp = f.getFakePlayer(id);
            } catch (IllegalArgumentException e) {
                for (FakePlayer p : f.getActiveFakePlayers()) if (p.getName().equalsIgnoreCase(target)) { fp = p; break; }
            }
            if (fp == null) {
                sender.sendMessage(Component.text("No active AFK for " + target, NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text("--- AFK info: " + fp.getName() + " (" + fp.getUniqueId() + ") ---", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("  reason: " + fp.getReason(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  timeout: " + fp.getTimeoutMinutes() + "m, expired=" + fp.isExpired(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  loc: " + fp.getLocation().getWorld().getName() + " " + fp.getLocation().getBlockX() + "," + fp.getLocation().getBlockY() + "," + fp.getLocation().getBlockZ(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  hidden=" + fp.isHidden() + " damageDisabled=" + fp.isDamageDisabled(), NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Usage: /unplugged-admin info <player|uuid>", NamedTextColor.RED));
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        String filter = args.length >= 2 ? args[1].toLowerCase() : "unplugged";
        var f = manager.factory();
        if (f == null) { sender.sendMessage(Component.text("Factory unavailable", NamedTextColor.RED)); return; }
        var all = f.getActiveFakePlayers();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("No active AFK bots", NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text("--- AFK bots (" + all.size() + ") ---", NamedTextColor.GOLD));
        for (FakePlayer fp : all) {
            if (filter.equals("players") || filter.equals("all")) {
                // could list all tracked, but we only have active
            }
            sender.sendMessage(Component.text(" - " + fp.getName() + " [" + fp.getUniqueId().toString().substring(0,8) + "] reason=" + fp.getReason() + " timeout=" + fp.getTimeoutMinutes() + "m", NamedTextColor.GRAY));
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /unplugged-admin set <key> <value>", NamedTextColor.RED));
            return;
        }
        var plugin = (com.warpmc.afk.WarpAFKPlugin) Bukkit.getPluginManager().getPlugin("WarpAFK");
        if (plugin == null) return;
        String key = args[1];
        String value = args[2];
        // simple handling for known keys
        switch (key) {
            case "default-timeout-minutes" -> plugin.getConfig().set("unplugged.default-timeout-minutes", Long.parseLong(value));
            case "reset-health-on-death" -> plugin.getConfig().set("unplugged.reset-health-on-death", Boolean.parseBoolean(value));
            case "disable-damage" -> plugin.getConfig().set("unplugged.disable-damage", Boolean.parseBoolean(value));
            case "hide-player" -> plugin.getConfig().set("unplugged.hide-player", Boolean.parseBoolean(value));
            default -> { sender.sendMessage(Component.text("Unknown key: " + key, NamedTextColor.RED)); return; }
        }
        plugin.saveConfig();
        sender.sendMessage(Component.text("Set " + key + " = " + value, NamedTextColor.GREEN));
    }
}
