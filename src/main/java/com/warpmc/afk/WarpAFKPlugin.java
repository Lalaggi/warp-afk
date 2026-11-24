package com.warpmc.afk;

import com.warpmc.afk.commands.AfkCommand;
import com.warpmc.afk.commands.UnplugCommand;
import com.warpmc.afk.commands.UnpluggedAdminCommand;
import com.warpmc.afk.listener.PlayerListener;
import com.warpmc.afk.manager.AFKManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.purpurmc.purpur.player.FakePlayer;

public final class WarpAFKPlugin extends JavaPlugin {

    private AFKManager afkManager;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        this.afkManager = new AFKManager(this);

        // Register AFKManager as a listener for FakePlayer lifecycle events
        getServer().getPluginManager().registerEvents(afkManager, this);

        // Register PlayerListener for real player join/quit events
        getServer().getPluginManager().registerEvents(new PlayerListener(this, afkManager), this);

        // Load persisted AFK bots from unplugged.json (slight delay so chunks are loaded first)
        getServer().getScheduler().runTaskLater(this, () -> {
            int restored = afkManager.loadAll();
            if (restored > 0) {
                getLogger().info("Restored " + restored + " persisted AFK bots");
            }
        }, 20L);

        var unplug = new UnplugCommand(afkManager);
        var admin = new UnpluggedAdminCommand(afkManager);
        var afk = new AfkCommand(afkManager);

        // Paper plugins use LifecycleEvents.COMMANDS + BasicCommand
        var lifecycle = this.getLifecycleManager();
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            if (getConfig().getBoolean("commands.enable-unplug", true)) {
                registrar.register("unplug", "Go unplugged — spawn a bot and disconnect", new BasicCommand() {
                    @Override public void execute(CommandSourceStack stack, String[] args) { unplug.onCommand(stack.getSender(), null, "unplug", args); }
                    @Override public String permission() { return "warpmc.afk.unplug"; }
                    @Override public java.util.Collection<String> suggest(CommandSourceStack stack, String[] args) {
                        if (args.length == 1) return suggestDurations(args[0]);
                        if (args.length == 2) return java.util.List.of("AFK");
                        return java.util.List.of();
                    }
                    private java.util.Collection<String> suggestDurations(String prefix) {
                        var list = java.util.List.of("30m","1h","4h","12h","1d","7d");
                        if (prefix.isEmpty()) return list;
                        return list.stream().filter(s -> s.startsWith(prefix)).toList();
                    }
                });
            }
            if (getConfig().getBoolean("commands.enable-afk", true)) {
                registrar.register("afk", "Alias for /unplug", new BasicCommand() {
                    @Override public void execute(CommandSourceStack stack, String[] args) { afk.onCommand(stack.getSender(), null, "afk", args); }
                    @Override public String permission() { return "warpmc.afk.unplug"; }
                });
            }
            registrar.register("unplugged-admin", "Admin control for unplugged bots", new BasicCommand() {
                @Override public void execute(CommandSourceStack stack, String[] args) { admin.onCommand(stack.getSender(), null, "unplugged-admin", args); }
                @Override public String permission() { return "warpmc.afk.admin"; }
                @Override public java.util.Collection<String> suggest(CommandSourceStack stack, String[] args) {
                    if (args.length == 1) return java.util.List.of("save","reload","purge","spawn","kick","info","list","set","help","about");
                    if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
                        var suggestions = new java.util.ArrayList<String>();
                        for (var p : getServer().getOnlinePlayers()) suggestions.add(p.getName());
                        return suggestions;
                    }
                    if (args.length == 2 && args[0].equalsIgnoreCase("kick")) {
                        var f = afkManager.factory();
                        if (f != null) return f.getActiveFakePlayers().stream().map(FakePlayer::getName).toList();
                        return java.util.List.of();
                    }
                    if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
                        var f = afkManager.factory();
                        if (f != null) return f.getActiveFakePlayers().stream().map(FakePlayer::getName).toList();
                        return java.util.List.of();
                    }
                    if (args.length == 2 && args[0].equalsIgnoreCase("set")) return java.util.List.of(
                        "default-timeout-minutes","reset-health-on-death","disable-damage",
                        "hide-player","hide-from-ops","broadcast","hide-join-leave",
                        "display-duration","reduced-list-debug-info","advanced-admin-options","enabled","debug"
                    );
                    if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                        String key = args[1];
                        var cfg = getConfig();
                        try {
                            if (key.equals("hide-player") || key.equals("hide-from-ops") || key.equals("disable-damage")
                                || key.equals("reset-health-on-death") || key.equals("broadcast") || key.equals("hide-join-leave")
                                || key.equals("display-duration")) {
                                return java.util.List.of("true","false");
                            }
                            if (key.equals("default-timeout-minutes")) return java.util.List.of(String.valueOf(cfg.getLong("unplugged.default-timeout-minutes")));
                        } catch (Exception ignored) {}
                    }
                    return java.util.List.of();
                }
            });
        });

        getLogger().info("WarpAFK enabled — FakePlayer API: " + (afkManager.isFactoryAvailable() ? "available" : "UNAVAILABLE"));
        if (!afkManager.isFactoryAvailable()) {
            getLogger().warning("FakePlayerFactory not available! Is this running on WarpMC / Purpur with FakePlayer patches?");
        }
    }

    @Override
    public void onDisable() {
        if (afkManager != null) {
            afkManager.saveAll();
        }
    }

    public AFKManager getAfkManager() {
        return afkManager;
    }
}
