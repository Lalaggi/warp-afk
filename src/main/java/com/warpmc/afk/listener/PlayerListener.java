package com.warpmc.afk.listener;

import com.warpmc.afk.manager.AFKManager;
import com.warpmc.afk.WarpAFKPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.purpurmc.purpur.event.FakePlayerSpawnEvent;
import org.purpurmc.purpur.event.FakePlayerRemoveEvent;
import org.purpurmc.purpur.player.FakePlayer;

/**
 * Listens for fake player lifecycle events and real player join/quit.
 * <p>
 * Tab-list visibility is now handled by the FakePlayer API
 * ({@code hiddenFromNonOps}), so this listener only manages broadcast
 * messages for real players returning from AFK.
 */
public class PlayerListener implements Listener {

    private final WarpAFKPlugin plugin;
    private final AFKManager manager;

    public PlayerListener(WarpAFKPlugin plugin, AFKManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var f = manager.factory();
        if (f == null) return;

        // If real player joins and had an active fake that was evicted, suppress
        // their join message if hide-join-leave is enabled (upstream hideUnpluggedJoin)
        boolean hideJoinLeave = plugin.getConfig().getBoolean("messages.hide-join-leave", false);
        if (hideJoinLeave) {
            // Check if this player had a persisted AFK session that was just evicted
            var persisted = manager.readPersistedData();
            for (var s : persisted) {
                try {
                    if (s.get("uuid").toString().equals(player.getUniqueId().toString())) {
                        event.joinMessage(null);
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        // If real player joins and has an active fake, it should have been evicted
        // by the server's login hook. Just save state.
        if (f.isFakePlayer(player.getUniqueId())) {
            manager.saveAll();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        // Suppress quit message for hidden fake players (handled by API, but
        // double-suppress for safety if config says so)
        if (player instanceof FakePlayer fp && fp.isHidden()
            && plugin.getConfig().getBoolean("messages.hide-join-leave", false)) {
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFakeSpawn(FakePlayerSpawnEvent event) {
        // FakePlayerSpawnEvent is also handled by AFKManager (saveAll).
        // Any plugin-specific logic goes here.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFakeRemove(FakePlayerRemoveEvent event) {
        // FakePlayerRemoveEvent is also handled by AFKManager (saveAll + broadcast).
    }
}
