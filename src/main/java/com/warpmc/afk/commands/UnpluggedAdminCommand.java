package com.warpmc.afk.commands;

import com.warpmc.afk.manager.AFKManager;
import com.warpmc.afk.util.DurationParser;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.purpurmc.purpur.player.FakePlayer;

/**
 * Admin command — polished to match upstream unplugged-afk quality.
 * Supports: save/reload/purge/spawn/kick/info/list/set + about when no args.
 * Respects main.reduced-list-debug-info and main.advanced-admin-options.
 */
public class UnpluggedAdminCommand implements CommandExecutor {

    private final AFKManager manager;

    public UnpluggedAdminCommand(AFKManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return handleAbout(sender);
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "save" -> {
                manager.saveAll();
                sender.sendMessage(Component.text("Saved unplugged.json", NamedTextColor.GREEN));
            }
            case "reload" -> {
                var plugin = (com.warpmc.afk.WarpAFKPlugin) Bukkit.getPluginManager().getPlugin("WarpAFK");
                if (plugin != null) {
                    plugin.reloadConfig();
                    sender.sendMessage(Component.text("Reloaded config.yml", NamedTextColor.GREEN));
                    // re-apply visibility after reload (hide settings may have changed)
                    applyVisibilityPostReload();
                } else {
                    sender.sendMessage(Component.text("Plugin not found", NamedTextColor.RED));
                }
            }
            case "purge" -> {
                manager.purgeAll();
                sender.sendMessage(Component.text("Purged all AFK bots and cleared persistence", NamedTextColor.GREEN));
            }
            case "spawn" -> handleSpawn(sender, args);
            case "kick" -> handleKick(sender, args);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender, args);
            case "set" -> handleSet(sender, args);
            case "help", "about" -> handleAbout(sender);
            default -> sender.sendMessage(Component.text("Unknown subcommand: " + sub + " — try /unplugged-admin help", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleAbout(CommandSender sender) {
        var plugin = (com.warpmc.afk.WarpAFKPlugin) Bukkit.getPluginManager().getPlugin("WarpAFK");
        String ver = plugin != null ? plugin.getDescription().getVersion() : "unknown";
        boolean enabled = plugin != null && plugin.getConfig().getBoolean("main.enabled", true);
        int active = manager.getAll().size();
        int persisted = manager.readPersistedData().size();
        sender.sendMessage(Component.text("— WarpAFK v" + ver + " (port of unplugged-afk) —", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  Enabled: " + enabled + " | Active bots: " + active + " | Persisted: " + persisted, NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Usage: /unplugged-admin <save|reload|purge|spawn|kick|info|list|set>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Wiki: https://github.com/sakura-ryoko/unplugged-afk", NamedTextColor.DARK_GRAY)
            .clickEvent(ClickEvent.openUrl("https://github.com/sakura-ryoko/unplugged-afk")));
        // Show brief config overview respecting reduced debug
        if (plugin != null) {
            boolean reduced = plugin.getConfig().getBoolean("main.reduced-list-debug-info", true);
            if (!reduced) {
                sender.sendMessage(Component.text("  Config: hide-player=" + plugin.getConfig().getBoolean("unplugged.hide-player")
                    + " hide-from-ops=" + plugin.getConfig().getBoolean("unplugged.hide-from-ops")
                    + " disable-damage=" + plugin.getConfig().getBoolean("unplugged.disable-damage"), NamedTextColor.DARK_GRAY));
            }
        }
        return true;
    }

    private void applyVisibilityPostReload() {
        // Re-apply hidding for all online viewers vs all active fakes (mirrors PlayerListener)
        try {
            var plugin = (com.warpmc.afk.WarpAFKPlugin) Bukkit.getPluginManager().getPlugin("WarpAFK");
            if (plugin == null) return;
            boolean hidePlayer = plugin.getConfig().getBoolean("unplugged.hide-player", false);
            boolean hideFromOps = plugin.getConfig().getBoolean("unplugged.hide-from-ops", false);
            var f = manager.factory();
            if (f == null) return;
            for (var fp : f.getActiveFakePlayers()) {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.getUniqueId().equals(fp.getUniqueId())) continue;
                    if (!hidePlayer) {
                        try { viewer.listPlayer((Player) fp); } catch (Exception ignored) {}
                    } else {
                        boolean shouldHide = hideFromOps || !viewer.isOp();
                        try {
                            if (shouldHide) viewer.unlistPlayer((Player) fp);
                            else viewer.listPlayer((Player) fp);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /unplugged-admin spawn <player> [minutes|duration] [reason]", NamedTextColor.RED));
            sender.sendMessage(Component.text("  Example: /unplugged-admin spawn Steve 4h farming", NamedTextColor.GRAY));
            return;
        }
        String targetName = args[1];
        long minutes = manager.getDefaultTimeout();
        String reason = null;
        if (args.length >= 3) {
            try {
                minutes = DurationParser.parseMinutes(args[2]);
                if (args.length >= 4) reason = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
            } catch (NumberFormatException e) {
                reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            }
        }
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null && online.isOnline()) {
            if (manager.factory() != null && manager.factory().isFakePlayer(online.getUniqueId())) {
                sender.sendMessage(Component.text("Player " + online.getName() + " is already AFK (has active bot)", NamedTextColor.YELLOW));
                return;
            }
            try {
                manager.spawnForPlayer(online, minutes, reason);
                sender.sendMessage(Component.text("Spawned AFK bot for " + online.getName() + " for " + formatDuration(minutes), NamedTextColor.GREEN));
                // Mimic upstream: disconnect was not needed for admin spawn — online player stays, bot is separate?
                // For spawnForPlayer we spawn bot and keep real online? Actually spawnForPlayer expects to spawn bot and then caller kicks.
                // Since this is admin forcing, don't kick the online player automatically.
            } catch (Exception e) {
                sender.sendMessage(Component.text("Failed: " + e.getMessage(), NamedTextColor.RED));
            }
            return;
        }
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
                world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                loc = world != null ? new Location(world, world.getSpawnLocation().getX(), world.getSpawnLocation().getY(), world.getSpawnLocation().getZ()) : null;
            }
        } else {
            sender.sendMessage(Component.text("Player not found: " + targetName + " (never joined)", NamedTextColor.RED));
            return;
        }
        // Check if already active
        if (manager.factory() != null && manager.factory().isFakePlayer(uuid)) {
            sender.sendMessage(Component.text("Player " + name + " is already AFK", NamedTextColor.YELLOW));
            return;
        }
        try {
            manager.spawnForOffline(uuid, name, world, loc, minutes, reason);
            sender.sendMessage(Component.text("Spawned AFK bot for " + name + " for " + formatDuration(minutes), NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /unplugged-admin kick <player|uuid>", NamedTextColor.RED));
            return;
        }
        String target = args[1];
        UUID uuid = null;
        if (target.length() > 16) {
            try { uuid = UUID.fromString(target); } catch (IllegalArgumentException ignored) {}
        }
        if (uuid == null) {
            Player online = Bukkit.getPlayerExact(target);
            if (online != null) uuid = online.getUniqueId();
            else {
                var off = Bukkit.getOfflinePlayer(target);
                if (off.hasPlayedBefore()) uuid = off.getUniqueId();
                else {
                    // Try active fake by name
                    var f = manager.factory();
                    if (f != null) {
                        for (FakePlayer fp : f.getActiveFakePlayers()) {
                            if (fp.getName().equalsIgnoreCase(target)) { uuid = fp.getUniqueId(); break; }
                        }
                    }
                }
            }
        }
        if (uuid == null) {
            sender.sendMessage(Component.text("Player not found: " + target, NamedTextColor.RED));
            return;
        }
        boolean removed = manager.remove(uuid);
        if (removed) {
            sender.sendMessage(Component.text("Kicked AFK bot for " + target, NamedTextColor.GREEN));
            var plugin = (com.warpmc.afk.WarpAFKPlugin) Bukkit.getPluginManager().getPlugin("WarpAFK");
            if (plugin != null && plugin.getConfig().getBoolean("messages.broadcast", false)) {
                String tmpl = plugin.getConfig().getString("messages.terminated", "<gray><player>'s AFK session was terminated");
                String timeDate = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                String msg = tmpl.replace("<player>", target)
                    .replace("<timeDate>", timeDate);
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(msg));
            }
        } else {
            sender.sendMessage(Component.text("No active AFK bot for " + target, NamedTextColor.YELLOW));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        var f = manager.factory();
        if (f == null) { sender.sendMessage(Component.text("Factory unavailable", NamedTextColor.RED)); return; }
        var plugin = (com.warpmc.afk.WarpAFKPlugin) Bukkit.getPluginManager().getPlugin("WarpAFK");
        boolean reduced = plugin != null && plugin.getConfig().getBoolean("main.reduced-list-debug-info", true);
        if (args.length == 1) {
            // No target: list brief info for all active
            var all = f.getActiveFakePlayers();
            if (all.isEmpty()) {
                sender.sendMessage(Component.text("No active AFK bots", NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text("--- AFK bots (" + all.size() + ") ---", NamedTextColor.GOLD));
            for (FakePlayer fp : all) sender.sendMessage(formatFakeBrief(fp, reduced));
            return;
        }
        String target = args[1];
        FakePlayer fp = null;
        try {
            UUID id = UUID.fromString(target);
            fp = f.getFakePlayer(id);
        } catch (IllegalArgumentException e) {
            for (FakePlayer p : f.getActiveFakePlayers()) if (p.getName().equalsIgnoreCase(target)) { fp = p; break; }
        }
        if (fp == null) {
            sender.sendMessage(Component.text("No active AFK for " + target, NamedTextColor.YELLOW));
            // Also check persisted offline data
            var persisted = manager.readPersistedData();
            for (var s : persisted) {
                String suuid = s.get("uuid") != null ? s.get("uuid").toString() : "";
                String sname = s.get("name") != null ? s.get("name").toString() : "";
                if (suuid.equalsIgnoreCase(target) || sname.equalsIgnoreCase(target)) {
                    long timeout = s.get("timeoutMinutes") != null ? Long.parseLong(s.get("timeoutMinutes").toString()) : -1;
                    sender.sendMessage(Component.text("  Found persisted offline: " + sname + " [" + suuid.substring(0, Math.min(8, suuid.length())) + "] timeout=" + timeout + "m", NamedTextColor.GRAY));
                    return;
                }
            }
            return;
        }
        sender.sendMessage(Component.text("--- AFK info: " + fp.getName() + " (" + fp.getUniqueId() + ") ---", NamedTextColor.GOLD));
        if (reduced) {
            sender.sendMessage(Component.text("  reason: " + fp.getReason(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  timeout: " + fp.getTimeoutMinutes() + "m, expired=" + fp.isExpired(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  loc: " + fp.getLocation().getWorld().getName() + " " + fp.getLocation().getBlockX() + "," + fp.getLocation().getBlockY() + "," + fp.getLocation().getBlockZ(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  hidden=" + fp.isHidden() + " damageDisabled=" + fp.isDamageDisabled(), NamedTextColor.GRAY));
        } else {
            // Full debug like upstream
            sender.sendMessage(Component.text("  UUID: " + fp.getUniqueId(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Reason: " + fp.getReason(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Timeout: " + fp.getTimeoutMinutes() + "m | Expired: " + fp.isExpired(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Spawn: " + fp.getSpawnTime(), NamedTextColor.GRAY));
            var loc = fp.getLocation();
            sender.sendMessage(Component.text("  World: " + (loc.getWorld()!=null?loc.getWorld().getName():"?") + " | Pos: " + String.format("%.1f, %.1f, %.1f yaw=%.1f pitch=%.1f", loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Health: " + ((org.bukkit.entity.Player)fp).getHealth() + "/" + ((org.bukkit.entity.Player)fp).getMaxHealth() + " | Food: " + ((org.bukkit.entity.Player)fp).getFoodLevel(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  GameMode: " + ((org.bukkit.entity.Player)fp).getGameMode() + " | Flying: " + ((org.bukkit.entity.Player)fp).isFlying(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Flags: hidden=" + fp.isHidden() + " damageDisabled=" + fp.isDamageDisabled() + " resetHealth=" + fp.isResetHealthOnDeath(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Inventory: " + countItems((org.bukkit.entity.Player)fp) + " items", NamedTextColor.DARK_GRAY));
        }
    }

    private Component formatFakeBrief(FakePlayer fp, boolean reduced) {
        String base = " - " + fp.getName() + " [" + fp.getUniqueId().toString().substring(0,8) + "] reason=" + fp.getReason() + " timeout=" + fp.getTimeoutMinutes() + "m";
        if (!reduced) base += " hidden=" + fp.isHidden() + " loc=" + fp.getLocation().getBlockX() + "," + fp.getLocation().getBlockY() + "," + fp.getLocation().getBlockZ();
        return Component.text(base, NamedTextColor.GRAY).clickEvent(ClickEvent.suggestCommand("/unplugged-admin info " + fp.getName()));
    }

    private int countItems(Player p) {
        int c = 0;
        for (var it : p.getInventory().getContents()) if (it != null && !it.getType().isAir()) c++;
        return c;
    }

    private void handleList(CommandSender sender, String[] args) {
        String filter = args.length >= 2 ? args[1].toLowerCase() : "unplugged";
        var f = manager.factory();
        if (f == null) { sender.sendMessage(Component.text("Factory unavailable", NamedTextColor.RED)); return; }
        var plugin = (com.warpmc.afk.WarpAFKPlugin) Bukkit.getPluginManager().getPlugin("WarpAFK");
        boolean reduced = plugin != null && plugin.getConfig().getBoolean("main.reduced-list-debug-info", true);
        boolean advanced = plugin != null && plugin.getConfig().getBoolean("main.advanced-admin-options", false);

        if (!advanced && (filter.equals("players") || filter.equals("all"))) {
            sender.sendMessage(Component.text("Advanced list options require main.advanced-admin-options=true", NamedTextColor.YELLOW));
            filter = "unplugged";
        }

        switch (filter) {
            case "players" -> {
                var persisted = manager.readPersistedData();
                if (persisted.isEmpty()) {
                    sender.sendMessage(Component.text("No tracked players (persisted)", NamedTextColor.YELLOW));
                    return;
                }
                sender.sendMessage(Component.text("--- Tracked players (" + persisted.size() + ") ---", NamedTextColor.GOLD));
                for (var s : persisted) {
                    String name = s.get("name") != null ? s.get("name").toString() : "?";
                    String uuid = s.get("uuid") != null ? s.get("uuid").toString() : "";
                    String world = s.get("world") != null ? s.get("world").toString() : "?";
                    long timeout = s.get("timeoutMinutes") != null ? Long.parseLong(s.get("timeoutMinutes").toString()) : -1;
                    String line = " - " + name + " [" + uuid.substring(0, Math.min(8, uuid.length())) + "] " + world + " timeout=" + timeout + "m";
                    if (!reduced && s.get("x") != null && s.get("y") != null && s.get("z") != null) {
                        line += " pos=" + s.get("x") + "," + s.get("y") + "," + s.get("z");
                    }
                    sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
                }
            }
            case "all" -> {
                var active = f.getActiveFakePlayers();
                var persisted = manager.readPersistedData();
                sender.sendMessage(Component.text("--- All: " + active.size() + " active + " + persisted.size() + " persisted ---", NamedTextColor.GOLD));
                handleList(sender, new String[]{"list", "unplugged"});
                if (!persisted.isEmpty()) {
                    handleList(sender, new String[]{"list", "players"});
                }
            }
            default -> {
                var all = f.getActiveFakePlayers();
                if (all.isEmpty()) {
                    sender.sendMessage(Component.text("No active AFK bots", NamedTextColor.YELLOW));
                    return;
                }
                sender.sendMessage(Component.text("--- AFK bots (" + all.size() + ") ---", NamedTextColor.GOLD));
                for (FakePlayer fp : all) {
                    sender.sendMessage(formatFakeBrief(fp, reduced));
                }
            }
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /unplugged-admin set <key> <value>", NamedTextColor.RED));
            sender.sendMessage(Component.text("  Keys: default-timeout-minutes, reset-health-on-death, disable-damage, hide-player, hide-from-ops, broadcast, hide-join-leave, display-duration, reduced-list-debug-info, advanced-admin-options, enabled", NamedTextColor.GRAY));
            return;
        }
        var plugin = (com.warpmc.afk.WarpAFKPlugin) Bukkit.getPluginManager().getPlugin("WarpAFK");
        if (plugin == null) return;
        boolean advanced = plugin.getConfig().getBoolean("main.advanced-admin-options", false);
        if (!advanced) {
            sender.sendMessage(Component.text("Advanced admin options are disabled (main.advanced-admin-options=false)", NamedTextColor.RED));
            return;
        }
        String key = args[1];
        String value = args[2];
        try {
            switch (key) {
                case "default-timeout-minutes" -> plugin.getConfig().set("unplugged.default-timeout-minutes", Long.parseLong(value));
                case "reset-health-on-death" -> plugin.getConfig().set("unplugged.reset-health-on-death", Boolean.parseBoolean(value));
                case "disable-damage" -> plugin.getConfig().set("unplugged.disable-damage", Boolean.parseBoolean(value));
                case "hide-player" -> plugin.getConfig().set("unplugged.hide-player", Boolean.parseBoolean(value));
                case "hide-from-ops" -> plugin.getConfig().set("unplugged.hide-from-ops", Boolean.parseBoolean(value));
                case "broadcast" -> plugin.getConfig().set("messages.broadcast", Boolean.parseBoolean(value));
                case "hide-join-leave" -> plugin.getConfig().set("messages.hide-join-leave", Boolean.parseBoolean(value));
                case "display-duration" -> plugin.getConfig().set("messages.display-duration", Boolean.parseBoolean(value));
                case "reduced-list-debug-info" -> plugin.getConfig().set("main.reduced-list-debug-info", Boolean.parseBoolean(value));
                case "advanced-admin-options" -> plugin.getConfig().set("main.advanced-admin-options", Boolean.parseBoolean(value));
                case "enabled" -> plugin.getConfig().set("main.enabled", Boolean.parseBoolean(value));
                case "debug" -> plugin.getConfig().set("main.debug", Boolean.parseBoolean(value));
                default -> { sender.sendMessage(Component.text("Unknown key: " + key, NamedTextColor.RED)); return; }
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid value for " + key + ": " + value, NamedTextColor.RED));
            return;
        }
        plugin.saveConfig();
        sender.sendMessage(Component.text("Set " + key + " = " + value, NamedTextColor.GREEN));
        // Re-apply visibility if hide keys changed
        if (key.startsWith("hide-")) applyVisibilityPostReload();
    }

    private String formatDuration(long minutes) {
        if (minutes < 0) return "forever";
        if (minutes < 60) return minutes + "m";
        long h = minutes / 60;
        long m = minutes % 60;
        if (m == 0) return h + "h";
        return h + "h " + m + "m";
    }
}
