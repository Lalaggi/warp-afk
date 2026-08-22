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
import org.purpurmc.purpur.player.FakePlayer;

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
        // If real player rejoins and has active fake, the fake should have been removed in
        // ServerLoginPacketListenerImpl already. But if plugin's persistence still has entry,
        // ensure it's cleaned and optionally show return message.
        if (f.isFakePlayer(player.getUniqueId())) {
            // This shouldn't happen — fake should be gone — but handle if plugin spawned it.
            // WarpMC's login hook already calls fake.remove(); we just save.
            manager.saveAll();
            if (plugin.getConfig().getBoolean("messages.broadcast", false)) {
                var comp = Component.text(player.getName() + " returned — AFK bot removed", NamedTextColor.GRAY);
                plugin.getServer().broadcast(comp);
            }
        }
        // If fake is hidden and config says hide join, suppress join message
        if (player instanceof FakePlayer fp && fp.isHidden() && plugin.getConfig().getBoolean("messages.hide-join-leave", false)) {
            event.joinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (player instanceof FakePlayer fp && fp.isHidden() && plugin.getConfig().getBoolean("messages.hide-join-leave", false)) {
            event.quitMessage(null);
        }
    }
}
