package com.xcreate.disaster.room;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.api.room.ProvisionedRoom;
import com.xcreate.disaster.api.room.RoomProvisioner;
import com.xcreate.disaster.api.room.RoomRequest;
import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.map.MapDefinition;
import com.xcreate.disaster.map.MapPoint;
import com.xcreate.disaster.map.MapRegistry;
import com.xcreate.disaster.map.MapSelector;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 房间的注册表、分流器与限流器。
 *
 * <p>玩家点加入时的顺序：本来就在某个房间里 → 找最早的空位 → 自己开一间 → 排队。
 * 房间按创建顺序填充，先开的先坐满。</p>
 *
 * <p>销毁一个房间要按固定顺序来，顺序错了就是文件被占用或玩家被踢回登录界面：
 * 先传走玩家，再卸载世界，最后删目录。</p>
 *
 * <p>只在主线程操作。</p>
 */
public final class RoomManager {

    /** 大厅世界名。配了就用它，没配就把玩家放回主世界出生点。 */
    private static final String LOBBY_WORLD = "disaster_lobby";

    private final DisasterPlugin plugin;
    private final MapRegistry maps;
    private final MapSelector selector;
    private final RoomProvisioner provisioner;

    private PluginConfig config;

    /** 房间表，按创建顺序。分流靠这个顺序做到「先开的先坐满」。 */
    private final Map<String, Room> rooms = new LinkedHashMap<>();

    /** 玩家到房间的归属。判断一个人是不是已经在局里，只查这张表。 */
    private final Map<UUID, String> membership = new HashMap<>();

    private final RoomQueue queue;

    private BukkitTask reaper;

    public RoomManager(DisasterPlugin plugin, MapRegistry maps, MapSelector selector,
                       RoomProvisioner provisioner, PluginConfig config) {
        this.plugin = plugin;
        this.maps = maps;
        this.selector = selector;
        this.provisioner = provisioner;
        this.config = config;
        this.queue = new RoomQueue(config.rooms().queueLimit());
    }

    public void start() {
        int interval = Math.max(1, config.rooms().reapIntervalSeconds()) * 20;
        this.reaper = Bukkit.getScheduler().runTaskTimer(plugin, this::reap, interval, interval);
    }

    /** 关服清场。留下的副本目录会撑爆磁盘，所以要挨个销毁。 */
    public void shutdown() {
        if (reaper != null) {
            reaper.cancel();
            reaper = null;
        }
        for (Room room : new ArrayList<>(rooms.values())) {
            destroy(room, "shutdown");
        }
        queue.clear();
        provisioner.shutdown();
    }

    /** 重载配置。已经开着的房间不受影响，新配置从下一个房间开始生效。 */
    public void apply(PluginConfig config) {
        this.config = config;
    }

    // ---- 加入与离开 ----

    /**
     * 玩家请求加入。
     *
     * @param mapId 指定的地图 id，为空表示由抽图决定
     */
    public JoinOutcome join(Player player, String mapId) {
        Room current = roomOf(player.getUniqueId()).orElse(null);
        if (current != null) {
            return JoinOutcome.alreadyIn(current);
        }

        String wanted = mapId == null || mapId.isBlank() ? null : mapId.trim();
        if (wanted != null && !maps.exists(wanted)) {
            return JoinOutcome.failed("map-not-found");
        }

        Room open = firstOpen(wanted);
        if (open != null) {
            enter(open, player);
            return JoinOutcome.joined(open);
        }

        if (rooms.size() >= config.rooms().maxRooms()) {
            if (!queue.enqueue(player.getUniqueId())) {
                return JoinOutcome.failed("queue-full");
            }
            return JoinOutcome.queued(queue.positionOf(player.getUniqueId()));
        }

        MapDefinition map = pickMap(wanted);
        if (map == null) {
            return JoinOutcome.failed(wanted == null ? "no-map" : "map-not-playable");
        }

        Room room = create(map);
        if (room == null) {
            return JoinOutcome.failed("world-name-exhausted");
        }
        enter(room, player);
        return JoinOutcome.creating(room);
    }

    /** 离开房间，同时退出排队。本来就不在房间里时返回 false。 */
    public boolean leave(Player player) {
        UUID playerId = player.getUniqueId();
        queue.remove(playerId);

        String roomId = membership.remove(playerId);
        if (roomId == null) {
            return false;
        }
        Room room = rooms.get(roomId);
        if (room == null) {
            return false;
        }
        room.leave(playerId);

        // 人走光了就从「等人开局」退回「空闲待加入」，好让下一个玩家直接进来
        if (room.isEmpty() && room.is(RoomState.WAITING)) {
            room.transition(RoomState.IDLE);
        }
        return true;
    }

    // ---- 查询 ----

    public Collection<Room> rooms() {
        return List.copyOf(rooms.values());
    }

    public Optional<Room> byId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String needle = token.trim().toLowerCase(Locale.ROOT);
        List<Room> partial = new ArrayList<>();
        for (Room room : rooms.values()) {
            String id = room.id().toLowerCase(Locale.ROOT);
            if (id.equals(needle)) {
                return Optional.of(room);
            }
            if (id.contains(needle)) {
                partial.add(room);
            }
        }
        // 只写地图 id 时可能命中多个副本，那种情况要求写全名
        return partial.size() == 1 ? Optional.of(partial.get(0)) : Optional.empty();
    }

    public Optional<Room> roomOf(UUID playerId) {
        String roomId = membership.get(playerId);
        if (roomId == null) {
            return Optional.empty();
        }
        Room room = rooms.get(roomId);
        if (room == null || room.is(RoomState.CLOSED)) {
            membership.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(room);
    }

    /** 正被房间占着的地图 id，抽图时要排除掉。 */
    public Set<String> occupiedMaps() {
        Set<String> occupied = new HashSet<>();
        for (Room room : rooms.values()) {
            if (room.state().holdsMap()) {
                occupied.add(room.mapId());
            }
        }
        return occupied;
    }

    public int queueSize() {
        return queue.size();
    }

    public int queuePosition(UUID playerId) {
        return queue.positionOf(playerId);
    }

    // ---- 销毁与回收 ----

    /** 关掉一个房间。玩家会被送回大厅，世界卸载，副本目录删掉。 */
    public void destroy(Room room, String reason) {
        if (room.is(RoomState.CLOSED)) {
            return;
        }
        if (!room.transition(RoomState.CLOSED)) {
            room.note("无法关闭：" + reason);
            return;
        }
        rooms.remove(room.id());

        List<UUID> evicted = room.evictAll();
        for (UUID playerId : evicted) {
            membership.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                sendHome(player);
                plugin.messages().send(player, "game.room-closed");
            }
        }

        ProvisionedRoom handle = room.handle();
        if (handle == null) {
            pullFromQueue();
            return;
        }
        provisioner.release(handle, this::pullFromQueue);
    }

    /** 手动回收全部房间。 */
    public int destroyAll(String reason) {
        List<Room> all = new ArrayList<>(rooms.values());
        for (Room room : all) {
            destroy(room, reason);
        }
        queue.clear();
        return all.size();
    }

    /** 空闲太久又没人的房间自动回收，返回回收数量。 */
    public int reap() {
        long timeout = config.rooms().idleTimeoutMillis();
        if (timeout <= 0) {
            return 0;
        }
        List<Room> expired = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (room.is(RoomState.IDLE) && room.isEmpty() && room.emptyMillis() >= timeout) {
                expired.add(room);
            }
        }
        for (Room room : expired) {
            plugin.getLogger().info("回收空闲房间 " + room.id()
                    + "（空置 " + room.emptyMillis() / 1000 + " 秒）。");
            destroy(room, "idle-timeout");
        }
        return expired.size();
    }

    // ---- 内部 ----

    /** 最早创建的、还能进人的房间。 */
    private Room firstOpen(String mapId) {
        for (Room room : rooms.values()) {
            if (!room.state().acceptsPlayers()) {
                continue;
            }
            if (mapId != null && !room.mapId().equalsIgnoreCase(mapId)) {
                continue;
            }
            if (isFull(room)) {
                continue;
            }
            return room;
        }
        return null;
    }

    private boolean enter(Room room, Player player) {
        if (!room.join(player.getUniqueId(), capacity(room))) {
            return false;
        }
        membership.put(player.getUniqueId(), room.id());
        // 有人了就从「空闲待加入」进入「等人开局」。
        // 不切的话房间表面上是空的、实际有人，运行循环不会开局，回收器也会一直盯着它。
        // 世界还没拷好的房间保持 CREATING，那份状态由创建回调按「有没有人」决定落点。
        if (room.is(RoomState.IDLE)) {
            room.transition(RoomState.WAITING);
        }
        teleport(room, player, room.size() - 1);
        return true;
    }

    private Room create(MapDefinition map) {
        String worldName = RoomNaming.nextWorldName(map.id(), takenWorldNames());
        if (worldName == null) {
            return null;
        }

        // 模板世界如果开着，刚做的改动可能还在内存里，先落盘再拷，免得副本拿到旧地形
        World template = maps.loadedTemplateWorld(map);
        World.Environment environment = World.Environment.NORMAL;
        if (template != null) {
            template.save();
            environment = template.getEnvironment();
        }

        Room room = new Room(worldName, map.inWorld(worldName));
        rooms.put(room.id(), room);

        RoomRequest request = new RoomRequest(room.id(), worldName, maps.templateFolder(map),
                environment);
        provisioner.provision(request, result -> {
            if (!result.success()) {
                room.note(result.error());
                plugin.getLogger().warning("房间 " + room.id() + " 创建失败：" + result.error());
                notifyMembers(room, "game.room-failed", "reason", result.error());
                destroy(room, "provision-failed");
                return;
            }
            room.attach(result);
            // 拷贝期间可能有人等不及退出了，那就直接落到空闲，好让下一个玩家进来
            RoomState ready = room.isEmpty() ? RoomState.IDLE : RoomState.WAITING;
            if (!room.transition(ready)) {
                plugin.getLogger().warning("房间 " + room.id() + " 就绪时状态异常。");
                destroy(room, "state-error");
                return;
            }
            teleportAll(room);
            notifyMembers(room, "game.room-ready",
                    "room", room.id(), "map", room.map().displayName());
            pullFromQueue();
        });
        return room;
    }

    private MapDefinition pickMap(String mapId) {
        List<MapDefinition> pool = maps.playable();
        if (pool.isEmpty()) {
            return null;
        }
        // 投票还没做，先只走「管理员指定 > 配置模式」这条路径
        return selector.select(pool, mapId, Map.of(), occupiedMaps())
                .map(MapSelector.Pick::map)
                .orElse(null);
    }

    /** 房间空出来之后，按排队顺序往里补人。 */
    private void pullFromQueue() {
        while (!queue.isEmpty()) {
            Room open = firstOpen(null);
            if (open == null) {
                return;
            }
            UUID playerId = queue.peek().orElse(null);
            if (playerId == null) {
                return;
            }
            queue.poll();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !enter(open, player)) {
                continue;
            }
            plugin.messages().send(player, "game.joined-room",
                    "room", open.id(), "map", open.map().displayName());
        }
    }

    private void teleportAll(Room room) {
        List<UUID> members = room.players();
        for (int slot = 0; slot < members.size(); slot++) {
            Player player = Bukkit.getPlayer(members.get(slot));
            if (player != null) {
                teleport(room, player, slot);
            }
        }
    }

    /** 按加入顺序分配出生点，人比出生点多时从头轮着用。 */
    private void teleport(Room room, Player player, int slot) {
        World world = room.world();
        if (world == null) {
            return;
        }
        MapDefinition map = room.map();
        List<MapPoint> spawns = map.spawns();
        MapPoint point = spawns.isEmpty()
                ? map.spectatorSpawn()
                : spawns.get(Math.floorMod(slot, spawns.size()));
        if (point == null) {
            return;
        }
        player.teleport(point.toLocation(world));
    }

    /** 把玩家送回大厅。配了大厅世界就用它，否则回主世界出生点。 */
    private void sendHome(Player player) {
        World lobby = Bukkit.getWorld(LOBBY_WORLD);
        if (lobby == null) {
            List<World> worlds = Bukkit.getWorlds();
            lobby = worlds.isEmpty() ? null : worlds.get(0);
        }
        if (lobby == null) {
            return;
        }
        Location spawn = lobby.getSpawnLocation();
        player.teleport(spawn);
    }

    private void notifyMembers(Room room, String key, String... pairs) {
        for (UUID playerId : room.players()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.messages().send(player, key, pairs);
            }
        }
    }

    private boolean isFull(Room room) {
        int capacity = capacity(room);
        return capacity > 0 && room.size() >= capacity;
    }

    /** 地图自己配了上限就取它，但不超过全局上限。0 表示不限。 */
    public int capacity(Room room) {
        int globalMax = config.game().maxPlayers();
        int mapMax = room.map().maxPlayers();
        if (mapMax <= 0) {
            return globalMax;
        }
        return globalMax > 0 ? Math.min(mapMax, globalMax) : mapMax;
    }

    /** 开局所需人数。地图自己配了就取它。 */
    public int requiredPlayers(Room room) {
        int mapMin = room.map().minPlayers();
        return mapMin > 0 ? mapMin : config.game().minPlayers();
    }

    /** 已加载的世界、世界目录、以及手上的房间，三者的名字都不能再用。 */
    private Set<String> takenWorldNames() {
        Set<String> taken = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            taken.add(world.getName());
        }
        File[] children = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                taken.add(child.getName());
            }
        }
        taken.addAll(rooms.keySet());
        return taken;
    }
}
