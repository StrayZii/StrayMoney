package org.strayzii.strayMoney.Listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.strayzii.strayMoney.StrayMoney;

import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseRegisterer implements Listener {
    private final StrayMoney plugin;

    public DatabaseRegisterer(StrayMoney plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        try {
            plugin.getDatabaseManager().createPlayer(e.getPlayer().getUniqueId());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register player " + e.getPlayer().getName(), ex);
        }
    }
}
