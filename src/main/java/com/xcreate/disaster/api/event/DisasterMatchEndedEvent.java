package com.xcreate.disaster.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

/**
 * 一局结束。
 *
 * <p>本插件可能「多人同胜」，也可能「无人获胜」——所以判定胜负请直接看
 * {@link #survivors()} 是否为空，不要假设一定有赢家。</p>
 *
 * <p><b>线程约定</b>：在主线程触发。</p>
 */
public class DisasterMatchEndedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String matchId;
    private final String mapId;
    private final List<UUID> survivors;
    private final List<UUID> participants;
    private final int durationSeconds;

    public DisasterMatchEndedEvent(String matchId, String mapId, List<UUID> survivors,
                                   List<UUID> participants, int durationSeconds) {
        this.matchId = matchId;
        this.mapId = mapId;
        this.survivors = survivors == null ? List.of() : List.copyOf(survivors);
        this.participants = participants == null ? List.of() : List.copyOf(participants);
        this.durationSeconds = durationSeconds;
    }

    public String matchId() {
        return matchId;
    }

    public String mapId() {
        return mapId;
    }

    /** 存活到最后的玩家。为空表示本局无人获胜——这是合法结果，不是异常。 */
    public List<UUID> survivors() {
        return survivors;
    }

    /** 本局全部参与者（含中途淘汰者），用于计算参与度与结算。 */
    public List<UUID> participants() {
        return participants;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
