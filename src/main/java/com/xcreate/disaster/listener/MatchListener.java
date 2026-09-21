package com.xcreate.disaster.listener;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.room.Room;
import com.xcreate.disaster.room.RoomManager;
import com.xcreate.disaster.room.RoomState;
import com.xcreate.disaster.match.MatchDirector;
import com.xcreate.disaster.map.MapPoint;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * 对局里的死亡与重生。
 *
 * <p>死亡即出局：记下死因、记下凶手、通知对局，然后把玩家留在旁观模式里看完这一局。
 * 死亡本身不拦——玩家掉进自己挖的坑里摔死是玩法的一部分。</p>
 *
 * <p>旁观不参与任何判定，所以也不需要额外隔离。</p>
 */
public final class MatchListener implements Listener {

    private final DisasterPlugin plugin;
    private final RoomManager rooms;
    private final MatchDirector director;

    public MatchListener(DisasterPlugin plugin, RoomManager rooms, MatchDirector director) {
        this.plugin = plugin;
        this.rooms = rooms;
        this.director = director;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Room room = rooms.roomOf(player.getUniqueId()).orElse(null);
        // 不在对局里、或者这一局还没开（倒计时阶段），死就死了，跟玩法无关
        if (room == null || !room.is(RoomState.RUNNING)) {
            return;
        }
        director.eliminate(room, player.getUniqueId(), causeOf(player), killerOf(player));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Room room = rooms.roomOf(player.getUniqueId()).orElse(null);
        if (room == null || !room.is(RoomState.RUNNING)) {
            return;
        }
        boolean eliminated = director.matchOf(room.id())
                .map(match -> match.isEliminated(player.getUniqueId()))
                .orElse(false);
        if (!eliminated) {
            return;
        }

        World world = room.world();
        if (world == null) {
            return;
        }
        MapPoint spot = room.map().spectatorSpawn();
        event.setRespawnLocation(spot == null
                ? world.getSpawnLocation()
                : spot.toLocation(world));

        // 重生流程会把模式刷回去，切旁观得等这一 tick 走完
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(GameMode.SPECTATOR);
                plugin.messages().send(player, "game.spectating");
            }
        });
    }

    /**
     * 死因标识。
     *
     * <p>输出的是短名而不是服务端枚举名：它与玩家淘汰事件里写的那套一致，
     * 将来接到网页或机器人上时不用再翻译一遍。</p>
     */
    private static String causeOf(Player player) {
        if (killerOf(player) != null) {
            return "pvp";
        }
        EntityDamageEvent last = player.getLastDamageCause();
        if (last == null) {
            return "unknown";
        }
        return switch (last.getCause()) {
            case FALL -> "fall";
            case LAVA -> "lava";
            case FIRE, FIRE_TICK -> "fire";
            case DROWNING -> "drowning";
            case VOID -> "void";
            case LIGHTNING -> "lightning";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "explosion";
            case PROJECTILE -> "projectile";
            default -> last.getCause().name().toLowerCase(Locale.ROOT);
        };
    }

    /** 死于玩家之手时返回凶手。箭、雪球这类投射物归到放它的人头上。 */
    private static UUID killerOf(Player player) {
        EntityDamageEvent last = player.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player killer) {
            return killer.getUniqueId();
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter.getUniqueId();
        }
        return null;
    }
}
