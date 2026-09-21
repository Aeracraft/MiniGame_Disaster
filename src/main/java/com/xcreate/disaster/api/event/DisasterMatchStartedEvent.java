package com.xcreate.disaster.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

/**
 * 一局开始，{@code players} 是这一局的全体参与者。
 *
 * <p>主线程触发。监听者若要做 I/O 请自行异步化，别阻塞主线程。</p>
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
