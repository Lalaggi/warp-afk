package com.warpmc.afk;

import com.warpmc.afk.commands.AfkCommand;
import com.warpmc.afk.commands.UnplugCommand;
import com.warpmc.afk.commands.UnpluggedAdminCommand;
import com.warpmc.afk.listener.PlayerListener;
import com.warpmc.afk.manager.AFKManager;
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

        var pm = getServer().getPluginManager();
        // Register via commands map (paper-plugin.yml already declares them, but we set executors via getCommand)
        if (getCommand("unplug") != null) getCommand("unplug").setExecutor(unplug);
        if (getCommand("afk") != null) getCommand("afk").setExecutor(afk);
        if (getCommand("unplugged-admin") != null) getCommand("unplugged-admin").setExecutor(admin);

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
