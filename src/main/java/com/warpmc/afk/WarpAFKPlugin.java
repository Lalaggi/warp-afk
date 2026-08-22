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

public final class WarpAFKPlugin extends JavaPlugin {

    private AFKManager afkManager;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        this.afkManager = new AFKManager(this);
        this.afkManager.loadPersisted();

        getServer().getPluginManager().registerEvents(new PlayerListener(this, afkManager), this);

        var unplug = new UnplugCommand(afkManager);
        var admin = new UnpluggedAdminCommand(afkManager);
        var afk = new AfkCommand(afkManager);

        // Paper plugins (paper-plugin.yml) cannot use JavaPlugin#getCommand during enable —
        // must use LifecycleEvents.COMMANDS + BasicCommand (Paper 1.21+)
        var lifecycle = this.getLifecycleManager();
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register("unplug", "Go unplugged — spawn a bot and disconnect", new BasicCommand() {
                @Override public void execute(CommandSourceStack stack, String[] args) { unplug.onCommand(stack.getSender(), null, "unplug", args); }
                @Override public String permission() { return "warpmc.afk.unplug"; }
            });
            registrar.register("afk", "Alias for /unplug", new BasicCommand() {
                @Override public void execute(CommandSourceStack stack, String[] args) { afk.onCommand(stack.getSender(), null, "afk", args); }
                @Override public String permission() { return "warpmc.afk.unplug"; }
            });
            registrar.register("unplugged-admin", "Admin control for unplugged bots", new BasicCommand() {
                @Override public void execute(CommandSourceStack stack, String[] args) { admin.onCommand(stack.getSender(), null, "unplugged-admin", args); }
                @Override public String permission() { return "warpmc.afk.admin"; }
                @Override public java.util.Collection<String> suggest(CommandSourceStack stack, String[] args) {
                    if (args.length == 1) return java.util.List.of("save","reload","purge","spawn","kick","info","list","set");
                    if (args.length == 2 && args[0].equalsIgnoreCase("list")) return java.util.List.of("players","unplugged","all");
                    return java.util.List.of();
                }
            });
        });

        // Restore persisted bots slightly delayed (chunks may not be loaded yet)
        getServer().getScheduler().runTaskLater(this, () -> afkManager.restoreAllDelayed(), 40L);

        getLogger().info("WarpAFK enabled — FakePlayer API: " + (afkManager.isFactoryAvailable() ? "available" : "UNAVAILABLE"));
        if (!afkManager.isFactoryAvailable()) {
            getLogger().warning("FakePlayerFactory not available! Is this running on WarpMC / Purpur with FakePlayer patches?");
        }
    }

    @Override
    public void onDisable() {
        if (afkManager != null) {
            afkManager.saveAll();
            // Optionally keep bots alive across restart; do NOT purge here.
            // If config says not to persist, they will naturally expire via timeout.
        }
    }

    public AFKManager getAfkManager() {
        return afkManager;
    }
}
