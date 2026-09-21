package com.xcreate.disaster.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 一名玩家被淘汰。
 *
 * <p><b>线程约定</b>：在主线程触发。</p>
 */
public class DisasterPlayerEliminatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String matchId;
    private final UUID playerId;
    private final String playerName;
    private final String cause;
    private final int elapsedSeconds;
    private final UUID killerId;

    public DisasterPlayerEliminatedEvent(String matchId, UUID playerId, String playerName,
                                         String cause, int elapsedSeconds, UUID killerId) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.cause = cause;
        this.elapsedSeconds = elapsedSeconds;
        this.killerId = killerId;
    }

    public String matchId() {
        return matchId;
    }

    public UUID playerId() {
        return playerId;
    }

    /**
     * 淘汰时的游戏内名字。
     *
     * <p>注意：名字可以改，身份不能——做跨平台关联时<b>只能用 {@link #playerId()}</b>，
     * 名字仅用于展示。</p>
     */
    public String playerName() {
        return playerName;
    }

    /** 死因标识，例如 {@code lightning}、{@code lava}、{@code fall}、{@code pvp}。 */
    public String cause() {
        return cause;
    }

    public int elapsedSeconds() {
        return elapsedSeconds;
    }

    /** 若死于玩家之手，这里是凶手的 UUID；否则为 {@code null}。 */
    public UUID killerId() {
        return killerId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
