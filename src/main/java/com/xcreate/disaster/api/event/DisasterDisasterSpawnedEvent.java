package com.xcreate.disaster.api.event;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/**
 * 掷中并触发了一个灾难。
 *
 * <p>这是录像时间轴上最重要的语义信息之一：「第几分几秒砸的是流星雨」
 * 只有玩法插件知道，包级录像本身看不出来。</p>
 *
 * <p><b>线程约定</b>：在主线程触发。</p>
 */
public class DisasterDisasterSpawnedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String matchId;
    private final int waveIndex;
    private final int elapsedSeconds;
    private final String disasterId;
    private final boolean primary;
    private final List<Location> spawnPoints;

    public DisasterDisasterSpawnedEvent(String matchId, int waveIndex, int elapsedSeconds,
                                        String disasterId, boolean primary,
                                        List<Location> spawnPoints) {
        this.matchId = matchId;
        this.waveIndex = waveIndex;
        this.elapsedSeconds = elapsedSeconds;
        this.disasterId = disasterId;
        this.primary = primary;
        this.spawnPoints = spawnPoints == null ? List.of() : List.copyOf(spawnPoints);
    }

    public String matchId() {
        return matchId;
    }

    /** 第几波，从 1 开始。 */
    public int waveIndex() {
        return waveIndex;
    }

    /** 相对开局经过的秒数。 */
    public int elapsedSeconds() {
        return elapsedSeconds;
    }

    /** 灾难标识，例如 {@code meteor_shower}。 */
    public String disasterId() {
        return disasterId;
    }

    /** 是主灾难还是次灾难。 */
    public boolean primary() {
        return primary;
    }

    /**
     * 本次落点。全图覆盖型灾难（酸雨、洪水）天生没有落点，此处为空列表。
     */
    public List<Location> spawnPoints() {
        return spawnPoints;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
