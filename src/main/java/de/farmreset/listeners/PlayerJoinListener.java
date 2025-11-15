package de.farmreset.listeners;

import de.farmreset.FarmReset;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final FarmReset plugin;

    public PlayerJoinListener(FarmReset plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Zeige Bossbar beim Join
        plugin.getBossbarManager().startBossbar();
    }
}

