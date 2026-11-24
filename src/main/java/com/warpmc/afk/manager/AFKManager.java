package com.warpmc.afk.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.warpmc.afk.WarpAFKPlugin;
import com.warpmc.afk.util.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.purpurmc.purpur.Purpur;
import org.purpurmc.purpur.player.FakePlayer;
import org.purpurmc.purpur.player.FakePlayerFactory;
import org.purpurmc.purpur.event.FakePlayerSpawnEvent;
import org.purpurmc.purpur.event.FakePlayerRemoveEvent;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages AFK fake players for WarpAFK.
 * <p>
 * Relies on the enhanced FakePlayer API for:
 * <ul>
 *   <li>Built-in persistence ({@link FakePlayerFactory#saveAll(File)} / {@link FakePlayerFactory#loadAll(File, Consumer)})</li>
 *   <li>Per-viewer visibility ({@code hiddenFromNonOps})</li>
 *   <li>Native chunk loading (no plugin ticket workarounds needed)</li>
 *   <li>Lifecycle events ({@link FakePlayerSpawnEvent}, {@link FakePlayerRemoveEvent})</li>
 * </ul>
 */
public class AFKManager implements Listener {

    private final WarpAFKPlugin plugin;
    private final File persistFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Debounce: only save to disk at most once every 2 seconds
    private long lastSaveMillis = 0;
    private boolean savePending = false;
    private int saveTaskId = -1;

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

    /**
     * Spawns an AFK bot for a real player. The real player is expected to be
     * kicked by the caller after this returns.
     */
    public FakePlayer spawnForPlayer(Player player, long minutes, String reason) {
        FakePlayerFactory f = factory();
        if (f == null) throw new IllegalStateException("FakePlayerFactory unavailable — not running WarpMC?");
        if (!plugin.getConfig().getBoolean("main.enabled", true))
            throw new IllegalStateException("WarpAFK is disabled in config (main.enabled=false)");

        long timeout = minutes <= 0 ? getDefaultTimeout() : minutes;
        boolean disableDamage = plugin.getConfig().getBoolean("unplugged.disable-damage", false);
        boolean hidePlayer = plugin.getConfig().getBoolean("unplugged.hide-player", false);
        boolean hideFromOps = plugin.getConfig().getBoolean("unplugged.hide-from-ops", false);
        boolean resetHealth = plugin.getConfig().getBoolean("unplugged.reset-health-on-death", false);
        boolean suppressJoinLeave = plugin.getConfig().getBoolean("messages.hide-join-leave", false);
        boolean displayDuration = plugin.getConfig().getBoolean("messages.display-duration", false);

        // Evict existing fake with same UUID to prevent duplicates
        if (f.isFakePlayer(player.getUniqueId())) {
            f.removeFakePlayer(player.getUniqueId());
        }

        // Snapshot live data before spawn (player may be removed by server's duplicate handling)
        Location savedLoc = player.getLocation().clone();
        World savedWorld = player.getWorld();

        FakePlayer bot = f.createBuilder(player)
            .world(savedWorld)
            .location(savedLoc)
            .timeoutMinutes(timeout)
            .reason(reason)
            .damageDisabled(disableDamage)
            .hidden(hidePlayer)
            .hiddenFromNonOps(hideFromOps)
            .resetHealthOnDeath(resetHealth)
            .suppressJoinLeaveMessages(suppressJoinLeave)
            .displayDuration(displayDuration)
            .spawn();

        // Copy live player data to bot so it starts as a true clone
        try { copyLiveData(player, bot); } catch (Exception e) {
            plugin.getLogger().warning("Failed to copy player data to fake: " + e.getMessage());
        }

        broadcastStart(player.getName(), reason, timeout);
        // Persistence is handled by FakePlayerSpawnEvent listener (debounced saveAll)
        return bot;
    }

    public FakePlayer spawnForOffline(UUID uuid, String name, World world, Location loc,
                                      long minutes, String reason) {
        FakePlayerFactory f = factory();
        if (f == null) throw new IllegalStateException("FakePlayerFactory unavailable");
        if (!plugin.getConfig().getBoolean("main.enabled", true))
            throw new IllegalStateException("WarpAFK is disabled in config (main.enabled=false)");

        long timeout = minutes <= 0 ? getDefaultTimeout() : minutes;
        boolean disableDamage = plugin.getConfig().getBoolean("unplugged.disable-damage", false);
        boolean hidePlayer = plugin.getConfig().getBoolean("unplugged.hide-player", false);
        boolean hideFromOps = plugin.getConfig().getBoolean("unplugged.hide-from-ops", false);
        boolean resetHealth = plugin.getConfig().getBoolean("unplugged.reset-health-on-death", false);
        boolean suppressJoinLeave = plugin.getConfig().getBoolean("messages.hide-join-leave", false);
        boolean displayDuration = plugin.getConfig().getBoolean("messages.display-duration", false);

        if (f.isFakePlayer(uuid)) f.removeFakePlayer(uuid);

        Location spawnLoc = loc != null ? loc : (world != null ? new Location(world, 0, world.getHighestBlockYAt(0, 0) + 1, 0) : null);

        FakePlayer bot = f.createBuilder(uuid, name)
            .world(world)
            .location(spawnLoc)
            .timeoutMinutes(timeout)
            .reason(reason)
            .damageDisabled(disableDamage)
            .hidden(hidePlayer)
            .hiddenFromNonOps(hideFromOps)
            .resetHealthOnDeath(resetHealth)
            .suppressJoinLeaveMessages(suppressJoinLeave)
            .displayDuration(displayDuration)
            .spawn();

        broadcastStart(name, reason, timeout);
        return bot;
    }

    public boolean remove(UUID uuid) {
        FakePlayerFactory f = factory();
        if (f == null) return false;
        // Capture name before removal for broadcast
        FakePlayer fp = f.getFakePlayer(uuid);
        String name = fp != null ? fp.getName() : null;
        boolean removed = f.removeFakePlayer(uuid);
        if (removed && name != null) {
            broadcastEnd(name, "terminated");
        }
        return removed;
    }

    /**
     * Deep-copy live data from a real player to a fake player so the bot starts
     * as a true clone (inventory, health, gamemode, cooldowns, etc.).
     */
    private void copyLiveData(Player src, FakePlayer dst) {
        dst.getInventory().setContents(src.getInventory().getContents());
        dst.getInventory().setArmorContents(src.getInventory().getArmorContents());
        dst.getInventory().setItemInOffHand(src.getInventory().getItemInOffHand());
        try {
            dst.setHealth(src.getHealth());
            dst.setFoodLevel(src.getFoodLevel());
            dst.setSaturation(src.getSaturation());
            dst.setExhaustion(src.getExhaustion());
            dst.setExp(src.getExp());
            dst.setLevel(src.getLevel());
            dst.setTotalExperience(src.getTotalExperience());
            dst.getEnderChest().setContents(src.getEnderChest().getContents());
            for (var eff : src.getActivePotionEffects()) dst.addPotionEffect(eff);
            dst.setGameMode(src.getGameMode());
            if (src.isFlying() || src.getAllowFlight()) {
                dst.setAllowFlight(src.getAllowFlight());
                dst.setFlying(src.isFlying());
            }
            dst.setFireTicks(src.getFireTicks());
            dst.setRemainingAir(src.getRemainingAir());
        } catch (Exception ignored) {}
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
        boolean displayDur = plugin.getConfig().getBoolean("messages.display-duration", false);
        String duration = formatDuration(minutes);
        String timeDate = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String msg = tmpl.replace("<player>", playerName)
            .replace("<reason>", reason != null ? reason : "")
            .replace("<duration>", displayDur ? duration : "")
            .replace("<timeDate>", timeDate);
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(msg));
    }

    public void broadcastEnd(String playerName, String type) {
        if (!plugin.getConfig().getBoolean("messages.broadcast", false)) return;
        boolean displayReturnFeedback = plugin.getConfig().getBoolean("messages.display-return-feedback", false);
        if (!displayReturnFeedback && !type.equals("terminated")) return;

        String key = switch (type) {
            case "expired" -> "messages.expired";
            case "replaced" -> "messages.replaced";
            case "terminated" -> "messages.terminated";
            default -> "messages.ended";
        };
        String tmpl = plugin.getConfig().getString(key, "<gray><player> is no longer AFK");
        if (tmpl == null) return;
        String timeDate = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String msg = tmpl.replace("<player>", playerName)
            .replace("<timeDate>", timeDate);
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(msg));
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

    // --- persistence (delegated to FakePlayerFactory) ---

    public void saveAll() {
        try {
            FakePlayerFactory f = factory();
            if (f == null) return;
            f.saveAll(persistFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save unplugged.json", e);
        }
    }

    /**
     * Debounced save — schedules a disk write at most once every 2 seconds.
     * Call this instead of {@link #saveAll()} when persistence should be updated
     * but immediate I/O is not required.
     */
    public void saveAllDebounced() {
        long now = System.currentTimeMillis();
        if (now - lastSaveMillis < 2000) {
            savePending = true;
            if (saveTaskId == -1) {
                saveTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    saveTaskId = -1;
                    if (savePending) {
                        savePending = false;
                        lastSaveMillis = System.currentTimeMillis();
                        saveAll();
                    }
                }, 40L).getTaskId();
            }
            return;
        }
        lastSaveMillis = now;
        saveAll();
    }

    public int loadAll() {
        try {
            FakePlayerFactory f = factory();
            if (f == null) return 0;
            return f.loadAll(persistFile, null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load unplugged.json", e);
            return 0;
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

    /**
     * Reads persisted fake player data directly from the unplugged.json file.
     * Used by admin list commands to show tracked/offline players.
     */
    public List<Map<String, Object>> readPersistedData() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!persistFile.exists()) return result;
        try (FileReader r = new FileReader(persistFile)) {
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> list = gson.fromJson(r, listType);
            if (list != null) result.addAll(list);
        } catch (Exception ignored) {}
        return result;
    }

    // --- Event handlers ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFakeSpawn(FakePlayerSpawnEvent event) {
        // Debounced auto-save after each spawn so persistence stays current
        saveAllDebounced();
        if (plugin.getConfig().getBoolean("main.debug", false)) {
            plugin.getLogger().info("[WarpAFK] Bot spawned: " + event.getFakePlayer().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFakeRemove(FakePlayerRemoveEvent event) {
        // Debounced auto-save after each removal
        saveAllDebounced();

        String name = event.getFakePlayerName();
        if (name == null) {
            plugin.getLogger().info("[WarpAFK] Bot removed: " + event.getFakePlayerUuid() + " (" + event.getReason() + ")");
            return;
        }

        broadcastEnd(name, switch (event.getReason()) {
            case TIMEOUT -> "expired";
            case REAL_LOGIN -> "replaced";
            case DEATH_NO_REVIVE -> "expired";
            default -> "terminated";
        });
    }
}
