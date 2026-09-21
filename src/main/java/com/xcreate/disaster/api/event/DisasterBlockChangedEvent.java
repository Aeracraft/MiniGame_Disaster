package com.xcreate.disaster.api.event;

import com.xcreate.disaster.api.replay.BlockChange;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/**
 * 一批方块被改动。
 *
 * <p>所有方块变更都收敛到统一入口（为了增量维护高度图缓存），该入口同时也是向外界
 * 输出变更的采集点。</p>
 *
 * <p>按批次触发，默认每个 tick 合并一次。灾难高峰期一秒可能产生成百上千次变更，
 * 逐次触发会把事件总线淹掉，所以单次事件携带多条。</p>
 *
 * <p>主线程触发。</p>
 */
public class DisasterBlockChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String matchId;
    private final String cause;
    private final List<BlockChange> changes;

    public DisasterBlockChangedEvent(String matchId, String cause, List<BlockChange> changes) {
        this.matchId = matchId;
        this.cause = cause;
        this.changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public String matchId() {
        return matchId;
    }

    /** 这批变更的来源，例如某个灾难的标识。 */
    public String cause() {
        return cause;
    }

    public List<BlockChange> changes() {
        return changes;
    }

    public int size() {
        return changes.size();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
