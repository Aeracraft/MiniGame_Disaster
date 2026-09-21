package com.xcreate.disaster.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

/**
 * 一局开始。
 *
 * <p>语义事件：玩法插件用「事实层」的语言描述游戏里发生了什么，不含任何平台知识。
 * 监听者据此自行决定要做什么——录像、上报、统计，都与本插件无关。</p>
 *
 * <p><b>线程约定</b>：在主线程触发。监听者若需要做 I/O，必须自行异步化，
 * 绝不允许阻塞主线程。</p>
 */
public class DisasterMatchStartedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String matchId;
    private final String mapId;
    private final String roomId;
    private final List<UUID> players;
    private final long startedAtMillis;

    public DisasterMatchStartedEvent(String matchId, String mapId, String roomId,
                                     List<UUID> players, long startedAtMillis) {
        this.matchId = matchId;
        this.mapId = mapId;
        this.roomId = roomId;
        this.players = players == null ? List.of() : List.copyOf(players);
        this.startedAtMillis = startedAtMillis;
    }

    public String matchId() {
        return matchId;
    }

    public String mapId() {
        return mapId;
    }

    public String roomId() {
        return roomId;
    }

    public List<UUID> players() {
        return players;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
