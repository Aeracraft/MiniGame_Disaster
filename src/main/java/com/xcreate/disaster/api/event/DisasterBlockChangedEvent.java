package com.xcreate.disaster.api.event;

import com.xcreate.disaster.api.replay.BlockChange;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/**
 * 一批方块被改动。
 *
 * <p>本插件规定<b>所有方块变更必须收敛到统一入口</b>（为了增量维护高度图缓存），
 * 这个入口同时也是向外界输出变更的天然采集点。</p>
 *
 * <p><b>性能注意</b>：灾难高峰期每秒可能产生成百上千次变更。若逐次触发事件会
 * 淹没事件总线，因此本事件按<b>批次</b>触发（默认每个 tick 合并一次），
 * 单次携带多条变更。</p>
 *
 * <p><b>线程约定</b>：在主线程触发。</p>
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
