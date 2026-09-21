package com.xcreate.disaster.room;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 房间的生命周期。
 *
 * <p>转换是单向的，非法转换直接拒绝而不是静默接受——房间状态错乱会连带世界被删或玩家被踢，
 * 那种问题从日志里几乎查不出根因。</p>
 *
 * <p>{@link #RESETTING} 指对局结束后把世界还原成可再开一局的状态（本地后端是删掉重拷，
 * 房间预热上线后是换一份已备好的副本）。一期还没有对局流程，这个状态暂时只是给后续留位。</p>
 */
public enum RoomState {

    /** 世界正在拷贝与加载，还不能进人。 */
    CREATING,

    /** 已就绪且没人。这是新玩家默认进的那种房间。 */
    IDLE,

    /** 有人在等开局。 */
    WAITING,

    /** 开局倒计时。 */
    COUNTDOWN,

    /** 对局进行中。 */
    RUNNING,

    /** 结算阶段。 */
    ENDING,

    /** 正在还原世界，期间不可加入。 */
    RESETTING,

    /** 已销毁。终态，不会再变。 */
    CLOSED;

    private static final Map<RoomState, Set<RoomState>> ALLOWED = buildTransitions();

    public boolean canGoTo(RoomState next) {
        if (next == null) {
            return false;
        }
        Set<RoomState> allowed = ALLOWED.get(this);
        return allowed != null && allowed.contains(next);
    }

    /** 新玩家能否进入。倒计时与对局中一律不接人。 */
    public boolean acceptsPlayers() {
        return this == IDLE || this == WAITING;
    }

    /** 是否正占着一张地图。抽图时要按这个把占用中的排除掉。 */
    public boolean holdsMap() {
        return this != CLOSED;
    }

    public boolean isClosed() {
        return this == CLOSED;
    }

    /** 可读性更好的名字，用于日志与命令输出。 */
    public String lowerName() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static Map<RoomState, Set<RoomState>> buildTransitions() {
        Map<RoomState, Set<RoomState>> map = new EnumMap<>(RoomState.class);
        map.put(CREATING, Set.of(IDLE, CLOSED));
        map.put(IDLE, Set.of(WAITING, RESETTING, CLOSED));
        map.put(WAITING, Set.of(IDLE, COUNTDOWN, RESETTING, CLOSED));
        map.put(COUNTDOWN, Set.of(WAITING, RUNNING, CLOSED));
        map.put(RUNNING, Set.of(ENDING, CLOSED));
        map.put(ENDING, Set.of(RESETTING, CLOSED));
        map.put(RESETTING, Set.of(IDLE, CLOSED));
        map.put(CLOSED, Set.of());
        return Map.copyOf(map);
    }
}
