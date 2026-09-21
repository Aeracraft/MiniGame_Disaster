package com.xcreate.disaster.match;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.api.event.DisasterDisasterSpawnedEvent;
import com.xcreate.disaster.api.event.DisasterMatchEndedEvent;
import com.xcreate.disaster.api.event.DisasterMatchStartedEvent;
import com.xcreate.disaster.api.event.DisasterPlayerEliminatedEvent;
import com.xcreate.disaster.api.storage.MatchParticipant;
import com.xcreate.disaster.api.storage.MatchRecord;
import com.xcreate.disaster.api.storage.PlayerDelta;
import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.disaster.DisasterDefinition;
import com.xcreate.disaster.disaster.DisasterEffects;
import com.xcreate.disaster.disaster.DisasterRoll;
import com.xcreate.disaster.disaster.DisasterTier;
import com.xcreate.disaster.disaster.EffectContext;
import com.xcreate.disaster.disaster.SpawnContext;
import com.xcreate.disaster.disaster.SpawnOutcome;
import com.xcreate.disaster.disaster.SpawnTerrain;
import com.xcreate.disaster.disaster.WorldTerrain;
import com.xcreate.disaster.map.MapPoint;
import com.xcreate.disaster.room.Room;
import com.xcreate.disaster.room.RoomManager;
import com.xcreate.disaster.room.RoomState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 对局的运行循环。
 *
 * <p>每秒扫一遍所有房间，按房间当前状态往前推：凑够人就倒计时，倒计时完了就开局，
 * 开局后按间隔掷波，该收了就结算，结算完关掉房间。</p>
 *
 * <p>用「每秒扫房间列表」而不是「每局挂一串定时任务」：房间数是十几的量级，扫一遍几乎不花钱；
 * 而每局一堆任务在房间销毁时很容易漏取消，留下改已卸载世界的幽灵任务。</p>
 *
 * <p>只在主线程操作。</p>
 */
public final class MatchDirector {

    /** 结算展示时长（秒）。留点时间让玩家看清结果，再关房间。 */
    private static final int ENDING_SECONDS = 10;

    private static final long PERIOD_TICKS = 20L;

    private final DisasterPlugin plugin;
    private final RoomManager rooms;
    private final DisasterEffects effects;

    private PluginConfig config;

    private final Map<String, Run> runs = new HashMap<>();
    private BukkitTask ticker;

    /** 一局的计时与数据。按房间 id 存，房间关掉时清掉。 */
    private static final class Run {

        Match match;
        long countdownEndsAt;
        long nextWaveAt;
        long endingEndsAt;
    }

    public MatchDirector(DisasterPlugin plugin, RoomManager rooms, DisasterEffects effects,
                         PluginConfig config) {
        this.plugin = plugin;
        this.rooms = rooms;
        this.effects = effects;
        this.config = config;
    }

    public void start() {
        this.ticker = Bukkit.getScheduler()
                .runTaskTimer(plugin, () -> tick(), PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        runs.clear();
        plugin.blocks().matchId("");
    }

    /** 重载配置。正在跑的对局不受影响，节奏从下一波开始按新值走。 */
    public void apply(PluginConfig config) {
        this.config = config;
    }

    /** 某个房间当前的对局，还没开局时为空。 */
    public Optional<Match> matchOf(String roomId) {
        Run run = runs.get(roomId);
        return run == null || run.match == null ? Optional.empty() : Optional.of(run.match);
    }

    /** 正在跑的对局。诊断命令用。 */
    public List<Match> activeMatches() {
        List<Match> active = new ArrayList<>();
        for (Run run : runs.values()) {
            if (run.match != null) {
                active.add(run.match);
            }
        }
        return active;
    }

    /**
     * 淘汰一名玩家。
     *
     * <p>死亡与中途离场都走这里，玩家淘汰事件因此只会发一次——重复的淘汰记录会让
     * 结算里出现负数存活时长，也会让上报方多收一条脏数据。</p>
     *
     * @param cause 死因标识，例如 {@code lava}、{@code pvp}、{@code quit}
     */
    public boolean eliminate(Room room, UUID playerId, String cause, UUID killerId) {
        Run run = runs.get(room.id());
        if (run == null || run.match == null) {
            return false;
        }
        Match match = run.match;
        long now = System.currentTimeMillis();
        if (!match.eliminate(playerId, cause, killerId, match.elapsedSeconds(now))) {
            return false;
        }
        Bukkit.getPluginManager()
                .callEvent(new DisasterPlayerEliminatedEvent(match.matchId(), playerId,
                        match.nameOf(playerId), cause, match.elapsedSeconds(now), killerId));
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Set<String> live = new HashSet<>();

        for (Room room : rooms.rooms()) {
            live.add(room.id());
            Run run = runs.computeIfAbsent(room.id(), key -> new Run());
            switch (room.state()) {
                case WAITING -> waiting(room, run, now);
                case COUNTDOWN -> countdown(room, run, now);
                case RUNNING -> running(room, run, now);
                case ENDING -> ending(room, run, now);
                default -> {
                }
            }
        }
        runs.keySet().removeIf(id -> !live.contains(id));
    }

    private void waiting(Room room, Run run, long now) {
        int required = rooms.requiredPlayers(room);
        if (required <= 0 || room.size() < required) {
            return;
        }
        if (!room.transition(RoomState.COUNTDOWN)) {
            plugin.getLogger().warning("房间 " + room.id() + " 无法进入倒计时，当前状态 "
                    + room.state().lowerName() + "。");
            return;
        }
        int seconds = Math.max(1, config.game().prepareSeconds());
        run.countdownEndsAt = now + seconds * 1000L;
        notifyMembers(room, "game.countdown", "seconds", String.valueOf(seconds));
    }

    private void countdown(Room room, Run run, long now) {
        if (room.size() < rooms.requiredPlayers(room)) {
            if (room.transition(RoomState.WAITING)) {
                run.countdownEndsAt = 0;
                notifyMembers(room, "game.countdown-cancelled");
            }
            return;
        }
        long remain = run.countdownEndsAt - now;
        if (remain > 0) {
            notifyMembers(room, "game.countdown", "seconds",
                    String.valueOf((int) Math.ceil(remain / 1000.0)));
            return;
        }
        begin(room, run, now);
    }

    private void begin(Room room, Run run, long now) {
        World world = room.world();
        if (world == null) {
            plugin.getLogger().warning("房间 " + room.id() + " 没有世界，无法开局。");
            rooms.destroy(room, "no-world");
            return;
        }

        List<UUID> players = room.players();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            names.put(playerId, player == null ? "" : player.getName());
        }

        Match match = new Match(Match.idOf(room.id(), now), room.id(), room.mapId(),
                now, players, names);

        if (!room.transition(RoomState.RUNNING)) {
            plugin.getLogger().warning("房间 " + room.id() + " 无法进入进行中，当前状态 "
                    + room.state().lowerName() + "。");
            return;
        }

        run.match = match;
        run.nextWaveAt = now + waveIntervalMillis();

        // 一局一份随机源。配了固定种子时每局都复现同一条序列，照着日志就能重演
        plugin.reseedMatchRandom();
        plugin.blocks().matchId(match.matchId());

        spread(room, world, players);
        Bukkit.getPluginManager().callEvent(new DisasterMatchStartedEvent(
                match.matchId(), match.mapId(), match.roomId(), players, now));
        notifyMembers(room, "game.match-started");
        plugin.getLogger().info("对局 " + match.matchId() + " 开始，"
                + players.size() + " 人，地图 " + match.mapId() + "。");
    }

    /** 按加入顺序把玩家摆到出生点，顺手清掉他们进服时带着的东西。 */
    private void spread(Room room, World world, List<UUID> players) {
        List<MapPoint> spawns = room.map().spawns();
        for (int slot = 0; slot < players.size(); slot++) {
            Player player = Bukkit.getPlayer(players.get(slot));
            if (player == null) {
                continue;
            }
            player.setGameMode(GameMode.SURVIVAL);
            player.setFireTicks(0);
            player.setFoodLevel(20);
            player.getInventory().clear();
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
            if (!spawns.isEmpty()) {
                player.teleport(spawns.get(Math.floorMod(slot, spawns.size())).toLocation(world));
            }
        }
    }

    private void running(Room room, Run run, long now) {
        Match match = run.match;
        if (match == null) {
            // 状态在进行中却没有对局数据，只能收掉，免得房间卡在半路
            plugin.getLogger().warning("房间 " + room.id() + " 缺少对局数据，直接结束。");
            rooms.destroy(room, "match-state-lost");
            return;
        }

        dropQuitters(room, match, now);

        if (now >= run.nextWaveAt) {
            run.nextWaveAt = now + waveIntervalMillis();
            runWave(room, match, now);
        }

        if (match.isOver(now, config.game().matchDurationSeconds())) {
            end(room, run, match, now);
        }
    }

    /**
     * 对局中离开的人按出局处理。
     *
     * <p>不处理的话他们既不算死也不算活，这一局会一直等一个不会回来的人。</p>
     */
    private void dropQuitters(Room room, Match match, long now) {
        for (UUID playerId : match.alive()) {
            if (room.contains(playerId)) {
                continue;
            }
            if (match.eliminate(playerId, "quit", null, match.elapsedSeconds(now))) {
                Bukkit.getPluginManager().callEvent(new DisasterPlayerEliminatedEvent(
                        match.matchId(), playerId, match.nameOf(playerId), "quit",
                        match.elapsedSeconds(now), null));
            }
        }
    }

    private void runWave(Room room, Match match, long now) {
        World world = room.world();
        if (world == null) {
            return;
        }
        int wave = match.nextWave();
        DisasterRoll roll = plugin.waveRoller().roll(wave, match.usedDisasters());
        if (roll.isEmpty()) {
            plugin.getLogger().info("对局 " + match.matchId() + " 第 " + wave
                    + " 波没有可掷的灾种（候选池已空）。");
            return;
        }

        SpawnTerrain terrain = new WorldTerrain(world);
        SpawnContext context = new SpawnContext(world.getName(), room.map().bounds(),
                alivePoints(match), room.map().spawns(), room.map().anchors());

        List<String> names = new ArrayList<>();
        for (DisasterDefinition definition : roll.all()) {
            match.markUsed(definition.id());
            names.add(definition.displayName());

            SpawnOutcome outcome = plugin.spawnPlanner().plan(definition, context, terrain);
            List<MapPoint> points = outcome.points();
            int changed = effects.apply(
                    new EffectContext(world, terrain, plugin.blocks(), definition), points);

            if (!effects.has(definition.id())) {
                plugin.getLogger().info("对局 " + match.matchId() + " 第 " + wave + " 波掷中 "
                        + definition.id() + "，该灾种的效果还没实现，本次只落点不动方块。"
                        + "落点：" + outcome.summary());
            } else if (plugin.pluginConfig().debug()) {
                plugin.getLogger().info("对局 " + match.matchId() + " 第 " + wave + " 波 "
                        + definition.id() + " 改动 " + changed + " 个方块，" + outcome.summary());
            }

            fireSpawned(match, wave, definition, points, world, now);
        }
        notifyMembers(room, "game.wave", "wave", String.valueOf(wave),
                "disasters", String.join("、", names));
    }

    private void end(Room room, Run run, Match match, long now) {
        if (!room.transition(RoomState.ENDING)) {
            plugin.getLogger().warning("房间 " + room.id() + " 无法进入结算，当前状态 "
                    + room.state().lowerName() + "。");
            rooms.destroy(room, "match-end");
            return;
        }
        run.endingEndsAt = now + ENDING_SECONDS * 1000L;

        List<UUID> survivors = match.survivors();
        int duration = match.elapsedSeconds(now);

        Bukkit.getPluginManager().callEvent(new DisasterMatchEndedEvent(
                match.matchId(), match.mapId(), survivors, match.participants(), duration));

        announce(room, match, survivors, duration);
        save(match, now);
        plugin.getLogger().info("对局 " + match.matchId() + " 结束，存活 " + survivors.size()
                + " / " + match.participants().size() + "，用时 " + duration + " 秒。");
    }

    private void announce(Room room, Match match, List<UUID> survivors, int duration) {
        if (survivors.isEmpty()) {
            notifyMembers(room, "game.no-survivors");
            return;
        }
        List<String> names = new ArrayList<>(survivors.size());
        for (UUID playerId : survivors) {
            names.add(match.nameOf(playerId));
        }
        notifyMembers(room, "game.match-ended", "survivors", String.join("、", names),
                "seconds", String.valueOf(duration));
    }

    /**
     * 结算落库。
     *
     * <p>存储不可用时什么都不做——对局收尾不该因为数据库连不上而卡住。累积统计走原子增量，
     * 与明细分开写：明细供上报方自行聚合，增量供游戏内直接显示。</p>
     */
    private void save(Match match, long now) {
        MatchRecord record = match.toRecord(plugin.pluginConfig().serverId(), now);
        plugin.storage().provider().saveMatch(record).exceptionally(error -> {
            plugin.getLogger().warning("对局 " + match.matchId() + " 结算写入失败："
                    + error.getMessage());
            return null;
        });

        for (MatchParticipant participant : record.participants()) {
            PlayerDelta delta = PlayerDelta.ofMatch(participant.survived(),
                    participant.survived(), participant.survivalSeconds());
            plugin.storage().provider()
                    .applyDelta(participant.playerId(), participant.playerName(), delta)
                    .exceptionally(error -> {
                        plugin.getLogger().warning("玩家 " + participant.playerName()
                                + " 的累积统计写入失败：" + error.getMessage());
                        return null;
                    });
        }
    }

    private void ending(Room room, Run run, long now) {
        if (now < run.endingEndsAt) {
            return;
        }
        plugin.blocks().matchId("");
        rooms.destroy(room, "match-end");
    }

    private void fireSpawned(Match match, int wave, DisasterDefinition definition,
                             List<MapPoint> points, World world, long now) {
        List<Location> locations = new ArrayList<>(points.size());
        for (MapPoint point : points) {
            locations.add(point.toLocation(world));
        }
        Bukkit.getPluginManager().callEvent(new DisasterDisasterSpawnedEvent(
                match.matchId(), wave, match.elapsedSeconds(now), definition.id(),
                definition.tier() == DisasterTier.PRIMARY, locations));
    }

    /** 还活着的玩家的坐标。围着玩家取点的灾种要用，坐标属于副本世界。 */
    private List<MapPoint> alivePoints(Match match) {
        List<MapPoint> points = new ArrayList<>();
        for (UUID playerId : match.alive()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                points.add(MapPoint.of(player.getLocation(), player.getName()));
            }
        }
        return points;
    }

    private long waveIntervalMillis() {
        return Math.max(1, config.game().waveIntervalSeconds()) * 1000L;
    }

    private void notifyMembers(Room room, String key, String... pairs) {
        for (UUID playerId : room.players()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.messages().send(player, key, pairs);
            }
        }
    }
}
