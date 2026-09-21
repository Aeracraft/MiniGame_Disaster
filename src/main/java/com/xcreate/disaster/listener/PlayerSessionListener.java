package com.xcreate.disaster.listener;

import com.xcreate.disaster.DisasterPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 在会话边界上维护权限缓存。
 *
 * <p>退出时清掉是为了不攒内存；进入时也清一次，兜住上一轮退出事件没送到的情况
 * （上一次异常关闭之类）。</p>
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
    }
}
