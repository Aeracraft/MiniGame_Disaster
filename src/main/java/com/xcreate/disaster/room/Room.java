package com.xcreate.disaster.room;

import com.xcreate.disaster.api.room.ProvisionedRoom;
import com.xcreate.disaster.map.MapDefinition;
import org.bukkit.World;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 一局对局的容器。
 *
 * <p>绑定一张地图（{@link #map()} 是已经换到副本世界的那份定义），持有本局玩家与状态。
 * 名字沿用世界名——世界名在服务端内唯一，拿它当房间标识不必再维护映射。</p>
 *
 * <p>成员集合用插入序，出生点按这个顺序依次分配，先到的占前面的点位。</p>
 *
 * <p>只在主线程读写。房间的状态变更都由命令与调度器在主线程驱动，不做并发保护。</p>
 */
public final class Room {

    private final String id;
    private final String mapId;
    private final MapDefinition map;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final long createdAt;

    private RoomState state = RoomState.CREATING;
    private ProvisionedRoom handle;
    private long emptySince;
    private String note = "";

    /**
     * @param worldName 已分配好的副本世界名，同时也是房间标识
     * @param map       指向该世界的地图定义，由 {@link MapDefinition#inWorld(String)} 得到
     */
    public Room(String worldName, MapDefinition map) {
        this.id = worldName;
        this.mapId = map.id();
        this.map = map;
        this.createdAt = System.currentTimeMillis();
        this.emptySince = createdAt;
    }

    public String id() {
        return id;
    }

    public String mapId() {
        return mapId;
    }

    public MapDefinition map() {
        return map;
    }

    public RoomState state() {
        return state;
    }

    public boolean is(RoomState other) {
        return state == other;
    }

    /** 世界还没加载好时为 null。 */
    public World world() {
        return handle == null ? null : handle.world();
    }

    /** 副本目录，销毁时要删掉。 */
    public File folder() {
        return handle == null ? null : handle.folder();
    }

    ProvisionedRoom handle() {
        return handle;
    }

    /** 上次失败或变更的原因，只用于排查，不影响流程。 */
    public String note() {
        return note;
    }

    public void note(String note) {
        this.note = note == null ? "" : note;
    }

    public long createdAt() {
        return createdAt;
    }

    public long aliveMillis() {
        return System.currentTimeMillis() - createdAt;
    }

    /**
     * 状态转换。非法转换拒绝并返回 false，调用方应记日志——悄悄接受会让房间卡在错误的状态上。
     */
    public boolean transition(RoomState next) {
        if (!state.canGoTo(next)) {
            return false;
        }
        state = next;
        return true;
    }

    /**
     * 加人。只管两件事：不能重复，不能超员。
     *
     * <p>「这个状态能不能进人」由 {@link RoomManager} 判断——房间在 CREATING 期间也要先把人收下，
     * 等世界就绪再一起传进去，所以状态这一层不能卡在这里。</p>
     */
    public boolean join(UUID playerId, int limit) {
        if (playerId == null || players.contains(playerId)) {
            return false;
        }
        if (limit > 0 && players.size() >= limit) {
            return false;
        }
        players.add(playerId);
        emptySince = 0;
        return true;
    }

    public boolean leave(UUID playerId) {
        if (playerId == null || !players.remove(playerId)) {
            return false;
        }
        if (players.isEmpty()) {
            emptySince = System.currentTimeMillis();
        }
        return true;
    }

    public boolean contains(UUID playerId) {
        return playerId != null && players.contains(playerId);
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public int size() {
        return players.size();
    }

    /** 按加入顺序快照。遍历时改动成员集合不会影响它。 */
    public List<UUID> players() {
        return new ArrayList<>(players);
    }

    /** 清空成员并返回被移出的玩家，销毁房间时用它把人都传走。 */
    public List<UUID> evictAll() {
        List<UUID> evicted = new ArrayList<>(players);
        players.clear();
        emptySince = System.currentTimeMillis();
        return evicted;
    }

    /** 空置了多久。不为空时返回 0。 */
    public long emptyMillis() {
        return emptySince == 0 ? 0 : System.currentTimeMillis() - emptySince;
    }

    /** 世界就绪后接上。 */
    public void attach(ProvisionedRoom handle) {
        this.handle = handle;
    }
}
