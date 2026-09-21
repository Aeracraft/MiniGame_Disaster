package com.xcreate.disaster.listener;

import com.xcreate.disaster.DisasterPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 会话边界上的清理。
 *
 * <p>退出时把玩家从房间里摘掉，否则他下次上线会被当成还在局里，既进不了新房间，
 * 又占着旧房间的名额。</p>
 */
public final class PlayerSessionListener implements Listener {

    private final DisasterPlugin plugin;

    public PlayerSessionListener(DisasterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.permissions().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.permissions().forget(event.getPlayer().getUniqueId());
        if (plugin.rooms() != null) {
            plugin.rooms().leave(event.getPlayer());
        }
    }
}
