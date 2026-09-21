package com.xcreate.disaster.match;

import com.xcreate.disaster.api.storage.MatchParticipant;
import com.xcreate.disaster.api.storage.MatchRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一局对局。
 *
 * <p>只装数据与判定，不碰服务端——胜负规则、存活统计、结算记录都能脱离服务端单测。
 * 世界里的动作（传送、旁观、广播事件）由 {@link MatchDirector} 负责。</p>
 *
 * <p>每位参与者的名字在开局时快照一次：结算与上报都要带名字，而那时玩家可能已经离线，
 * 再查一次名字不但麻烦，还可能查不到。</p>
 */
public final class Match {

    /**
     * 存活人数降到这个值时提前结束。
     *
     * <p>只剩一个人就是既成事实，没必要让最后那位空等剩下的时间。</p>
     */
    private static final int LAST_STANDING = 1;

    /** 一次淘汰。 */
    public record Elimination(String cause, int elapsedSeconds, UUID killerId) {
    }

    private final String matchId;
    private final String roomId;
    private final String mapId;
    private final long startedAtMillis;
    private final List<UUID> participants;
    private final Map<UUID, String> names;

    /** 按淘汰顺序记录，结算时要算各自的存活时长。 */
    private final Map<UUID, Elimination> eliminations = new LinkedHashMap<>();

    /** 本局掷中过的灾种。持续型灾难也在里面——它们这一局不会再被掷中第二次。 */
    private final Set<String> usedDisasters = new LinkedHashSet<>();

    private int waveIndex;

    public Match(String matchId, String roomId, String mapId, long startedAtMillis,
                 List<UUID> participants, Map<UUID, String> names) {
        this.matchId = matchId;
        this.roomId = roomId;
        this.mapId = mapId;
        this.startedAtMillis = startedAtMillis;
        this.participants = List.copyOf(participants);
        this.names = Map.copyOf(names);
        this.waveIndex = 0;
    }

    /** 局标识：房间名加开局时间戳（36 进制）。同一房间同一毫秒开不出两局，够唯一，且看得出是哪间。 */
    public static String idOf(String roomId, long startedAtMillis) {
        return roomId + "-" + Long.toString(startedAtMillis, 36);
    }

    public String matchId() {
        return matchId;
    }

    public String roomId() {
        return roomId;
    }

    public String mapId() {
        return mapId;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public List<UUID> participants() {
        return participants;
    }

    public String nameOf(UUID playerId) {
        String name = names.get(playerId);
        return name == null ? "" : name;
    }

    public int elapsedSeconds(long nowMillis) {
        return (int) Math.max(0L, (nowMillis - startedAtMillis) / 1000L);
    }

    /** 当前是第几波，从 1 开始。还没掷过是 0。 */
    public int waveIndex() {
        return waveIndex;
    }

    /** 记下一波并返回它的序号。 */
    public int nextWave() {
        return ++waveIndex;
    }

    /**
     * 记一次淘汰。已经死过的人再死一次不会重复记账。
     *
     * @param cause 死因标识，例如 {@code lava}、{@code pvp}、{@code quit}
     */
    public boolean eliminate(UUID playerId, String cause, UUID killerId, int elapsedSeconds) {
        if (playerId == null || !participants.contains(playerId) || eliminations.containsKey(playerId)) {
            return false;
        }
        eliminations.put(playerId, new Elimination(cause, Math.max(0, elapsedSeconds), killerId));
        return true;
    }

    public boolean isEliminated(UUID playerId) {
        return eliminations.containsKey(playerId);
    }

    public Elimination eliminationOf(UUID playerId) {
        return eliminations.get(playerId);
    }

    /** 还活着的，按开局顺序。 */
    public List<UUID> alive() {
        List<UUID> alive = new ArrayList<>(participants.size());
        for (UUID playerId : participants) {
            if (!eliminations.containsKey(playerId)) {
                alive.add(playerId);
            }
        }
        return alive;
    }

    /** 活到最后的人。可能是空的——没人活下来是合法结果，不是异常。 */
    public List<UUID> survivors() {
        return alive();
    }

    /** 掷中一个灾种，本局不再重复掷它。 */
    public void markUsed(String disasterId) {
        if (disasterId != null && !disasterId.isBlank()) {
            usedDisasters.add(disasterId);
        }
    }

    /** 已掷中过的灾种，掷骰时从候选池里摘掉。 */
    public Set<String> usedDisasters() {
        return Set.copyOf(usedDisasters);
    }

    /**
     * 这一局该收了吗。
     *
     * <p>到时长该收；人死得只剩一个也该收。开局人数不足两人时不做「剩一个」的判定，
     * 否则管理员强制开局会立刻收局。</p>
     */
    public boolean isOver(long nowMillis, int durationSeconds) {
        if (durationSeconds > 0 && elapsedSeconds(nowMillis) >= durationSeconds) {
            return true;
        }
        return participants.size() >= 2 && alive().size() <= LAST_STANDING;
    }

    /**
     * 结算记录。
     *
     * @param serverId 跑这一局的子服标识，跨服时用于区分来源
     */
    public MatchRecord toRecord(String serverId, long endedAtMillis) {
        int duration = elapsedSeconds(endedAtMillis);
        List<MatchParticipant> records = new ArrayList<>(participants.size());
        for (UUID playerId : participants) {
            Elimination death = eliminations.get(playerId);
            records.add(new MatchParticipant(
                    playerId,
                    nameOf(playerId),
                    death == null,
                    death == null ? duration : death.elapsedSeconds(),
                    death == null ? null : death.cause()));
        }
        return new MatchRecord(matchId, mapId, roomId, serverId, startedAtMillis,
                endedAtMillis, duration, List.copyOf(usedDisasters), records);
    }
}
