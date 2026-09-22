package com.xcreate.disaster.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

/**
 * 同局玩家之间点了一次赞。
 *
 * <p>只有正向标签、没有自由文本——自由文本得另做内容审核，这个代价不值。</p>
 *
 * <p><b>线程约定</b>：在主线程触发。</p>
 */
public class DisasterPlayerRepEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String matchId;
    private final UUID fromId;
    private final UUID toId;
    private final List<String> tags;

    public DisasterPlayerRepEvent(String matchId, UUID fromId, UUID toId,
                                  List<String> tags) {
        this.matchId = matchId;
        this.fromId = fromId;
        this.toId = toId;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** 两人是在这一局里碰上的。 */
    public String matchId() {
        return matchId;
    }

    public UUID fromId() {
        return fromId;
    }

    public UUID toId() {
        return toId;
    }

    /** 赞的名目，取自固定选项。 */
    public List<String> tags() {
        return tags;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
