package com.warpmc.afk.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.warpmc.afk.WarpAFKPlugin;
import com.warpmc.afk.storage.SavedFake;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.purpurmc.purpur.Purpur;
import org.purpurmc.purpur.player.FakePlayer;
import org.purpurmc.purpur.player.FakePlayerFactory;

public class AFKManager {

    private final WarpAFKPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File persistFile;

    public AFKManager(WarpAFKPlugin plugin) {
        this.plugin = plugin;
        this.persistFile = new File(plugin.getDataFolder(), "unplugged.json");
    }

    public WarpAFKPlugin getPlugin() { return plugin; }

    public boolean isFactoryAvailable() {
        return Purpur.getFakePlayerFactory() != null;
    }

    public FakePlayerFactory factory() {
        return Purpur.getFakePlayerFactory();
    }

    // --- core spawn ---

    public FakePlayer spawnForPlayer(Player player, long minutes, String reason) {
        FakePlayerFactory f = factory();
        if (f == null) throw new IllegalStateException("FakePlayerFactory unavailable — not running WarpMC?");

        long timeout = minutes <= 0 ? getDefaultTimeout() : minutes;
        boolean disableDamage = plugin.getConfig().getBoolean("unplugged.disable-damage", false);
        boolean hide = plugin.getConfig().getBoolean("unplugged.hide-player", false);
        boolean resetHealth = plugin.getConfig().getBoolean("unplugged.reset-health-on-death", true);

        // If already has fake, remove it first
        if (f.isFakePlayer(player.getUniqueId())) {
            f.removeFakePlayer(player.getUniqueId());
        }

        FakePlayer bot = f.createBuilder(player)
            .world(player.getWorld())
            .location(player.getLocation())
            .timeoutMinutes(timeout)
            .reason(reason)
            .damageDisabled(disableDamage)
            .hidden(hide)
            .resetHealthOnDeath(resetHealth)
            .spawn();

        broadcastStart(player.getName(), reason, timeout);
        saveAll();
        return bot;
    }

    public FakePlayer spawnForOffline(UUID uuid, String name, World world, Location loc, long minutes, String reason) {
        FakePlayerFactory f = factory();
        if (f == null) throw new IllegalStateException("FakePlayerFactory unavailable");
        long timeout = minutes <= 0 ? getDefaultTimeout() : minutes;
        boolean disableDamage = plugin.getConfig().getBoolean("unplugged.disable-damage", false);
        boolean hide = plugin.getConfig().getBoolean("unplugged.hide-player", false);
        boolean resetHealth = plugin.getConfig().getBoolean("unplugged.reset-health-on-death", true);

        if (f.isFakePlayer(uuid)) f.removeFakePlayer(uuid);

        Location spawnLoc = loc != null ? loc : (world != null ? new Location(world, 0, world.getHighestBlockYAt(0,0)+1, 0) : null);
        var builder = f.createBuilder(uuid, name);
        if (world != null) builder.world(world);
        if (spawnLoc != null) builder.location(spawnLoc);
        FakePlayer bot = builder
            .timeoutMinutes(timeout)
            .reason(reason)
            .damageDisabled(disableDamage)
            .hidden(hide)
            .resetHealthOnDeath(resetHealth)
            .spawn();
        broadcastStart(name, reason, timeout);
        saveAll();
        return bot;
    }

    public boolean remove(UUID uuid) {
        FakePlayerFactory f = factory();
        if (f == null) return false;
        boolean removed = f.removeFakePlayer(uuid);
        if (removed) {
            saveAll();
            // broadcastEnd is handled by caller or quit listener
        }
        return removed;
    }

    public Collection<FakePlayer> getAll() {
        FakePlayerFactory f = factory();
        if (f == null) return List.of();
        return f.getActiveFakePlayers();
    }

    // --- messaging ---

    private void broadcastStart(String playerName, String reason, long minutes) {
        if (!plugin.getConfig().getBoolean("messages.broadcast", false)) return;
        String tmpl = plugin.getConfig().getString("messages.started", "<gray><player> is now AFK");
        String duration = formatDuration(minutes);
        String msg = tmpl.replace("<player>", playerName)
            .replace("<reason>", reason != null ? reason : "")
            .replace("<duration>", duration);
        Component comp = MiniMessage.miniMessage().deserialize(msg);
        Bukkit.broadcast(comp);
    }

    private String formatDuration(long minutes) {
        if (minutes < 0) return "forever";
        if (minutes < 60) return minutes + "m";
        long h = minutes / 60;
        long m = minutes % 60;
        if (m == 0) return h + "h";
        return h + "h " + m + "m";
    }

    public long getDefaultTimeout() {
        return plugin.getConfig().getLong("unplugged.default-timeout-minutes", 129600L);
    }

    // --- persistence ---

    public void saveAll() {
        try {
            FakePlayerFactory f = factory();
            if (f == null) return;
            List<SavedFake> list = new ArrayList<>();
            for (FakePlayer fp : f.getActiveFakePlayers()) {
                Location loc = fp.getLocation();
                SavedFake s = new SavedFake();
                s.uuid = fp.getUniqueId().toString();
                s.name = fp.getName();
                s.world = loc.getWorld() != null ? loc.getWorld().getName() : null;
                s.x = loc.getX();
                s.y = loc.getY();
                s.z = loc.getZ();
                s.yaw = loc.getYaw();
                s.pitch = loc.getPitch();
                s.timeoutMinutes = fp.getTimeoutMinutes();
                s.reason = fp.getReason();
                s.damageDisabled = fp.isDamageDisabled();
                s.hidden = fp.isHidden();
                s.resetHealthOnDeath = fp.isResetHealthOnDeath();
                s.spawnTimeMillis = fp.getSpawnTime() != null ? fp.getSpawnTime().toEpochMilli() : System.currentTimeMillis();
                list.add(s);
            }
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            try (FileWriter w = new FileWriter(persistFile)) {
                gson.toJson(list, w);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save unplugged.json", e);
        }
    }

    public List<SavedFake> loadPersistedData() {
        if (!persistFile.exists()) return List.of();
        try (FileReader r = new FileReader(persistFile)) {
            Type type = new TypeToken<List<SavedFake>>(){}.getType();
            List<SavedFake> list = gson.fromJson(r, type);
            return list != null ? list : List.of();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load unplugged.json", e);
            return List.of();
        }
    }

    public void loadPersisted() {
        // just ensure file exists; actual restore is delayed
    }

    public void restoreAllDelayed() {
        List<SavedFake> saved = loadPersistedData();
        if (saved.isEmpty()) return;
        FakePlayerFactory f = factory();
        if (f == null) return;
        int restored = 0;
        for (SavedFake s : saved) {
            try {
                UUID uuid = UUID.fromString(s.uuid);
                // Skip if already active or if real player is online (real takes precedence)
                if (f.isFakePlayer(uuid)) continue;
                if (Bukkit.getPlayer(uuid) != null) continue; // real is online, don't respawn bot
                long elapsedMinutes = (System.currentTimeMillis() - s.spawnTimeMillis) / 60000L;
                long remaining = s.timeoutMinutes < 0 ? -1 : s.timeoutMinutes - elapsedMinutes;
                if (s.timeoutMinutes >= 0 && remaining <= 0) continue; // expired while offline

                World world = s.world != null ? Bukkit.getWorld(s.world) : null;
                if (world == null) world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (world == null) continue;
                Location loc = new Location(world, s.x, s.y, s.z, s.yaw, s.pitch);

                // If elapsed >0, adjust timeout to remaining so original expiry is preserved
                long timeoutToUse = s.timeoutMinutes < 0 ? -1 : remaining;

                var builder = f.createBuilder(uuid, s.name)
                    .world(world)
                    .location(loc)
                    .timeoutMinutes(timeoutToUse)
                    .reason(s.reason)
                    .damageDisabled(s.damageDisabled)
                    .hidden(s.hidden)
                    .resetHealthOnDeath(s.resetHealthOnDeath);
                // Preserve original spawnTime semantics by spawning now but with remaining timeout
                builder.spawn();
                restored++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to restore fake " + s.name + " (" + s.uuid + ")", e);
            }
        }
        if (restored > 0) {
            plugin.getLogger().info("Restored " + restored + " persisted AFK bots");
            saveAll();
        }
    }

    public void purgeAll() {
        FakePlayerFactory f = factory();
        if (f != null) {
            List<UUID> toRemove = new ArrayList<>();
            for (FakePlayer fp : f.getActiveFakePlayers()) toRemove.add(fp.getUniqueId());
            for (UUID id : toRemove) f.removeFakePlayer(id);
        }
        if (persistFile.exists()) persistFile.delete();
    }
}
