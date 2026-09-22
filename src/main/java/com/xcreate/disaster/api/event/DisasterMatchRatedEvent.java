package com.xcreate.disaster.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

/**
 * 有人给一局打了分。
 *
 * <p>评价对外是匿名的。{@link #raterId()} 只用于查重与审计，展示给玩家或推给外部服务时
 * 应当只带星级与标签，别把评价人一并带出去。</p>
 *
 * <p><b>线程约定</b>：在主线程触发。</p>
 */
public class DisasterMatchRatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String matchId;
    private final UUID raterId;
    private final int stars;
    private final List<String> issueTags;

    public DisasterMatchRatedEvent(String matchId, UUID raterId, int stars,
                                   List<String> issueTags) {
        this.matchId = matchId;
        this.raterId = raterId;
        this.stars = stars;
        this.issueTags = issueTags == null ? List.of() : List.copyOf(issueTags);
    }

    public String matchId() {
        return matchId;
    }

    /** 只用于查重与审计，不对外展示。 */
    public UUID raterId() {
        return raterId;
    }

    /** 1–5 星。 */
    public int stars() {
        return stars;
    }

    /** 问题标签，取自固定选项。 */
    public List<String> issueTags() {
        return issueTags;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
