package com.xcreate.disaster.api.replay;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * 回放引擎的可插拔契约。
 *
 * <p>一期没有内置实现。Disaster 只定义接口、广播语义事件，第三方或后续自研的回放插件
 * 实现本接口后注册到 {@code ServicesManager} 即可接管。</p>
 *
 * <p>依赖方向单向：接口在 Disaster 侧，回放插件 {@code compileOnly} 依赖 Disaster，
 * 反过来不成立。Disaster 只查服务，查不到就跳过全部回放相关行为，不报错。</p>
 *
 * <p>实现方注意两点。所有方法都可能在主线程被调用，实现方自行异步化，别在里面做阻塞 I/O；
 * 房间世界打完结会被删除，而录像里烧着世界名，加载录像时要自己做重映射。</p>
 */
public interface ReplayProvider {

    /** 实现标识，例如 {@code advancedreplay} / {@code native}。 */
    String id();

    /** 依赖缺失、存储不可用等情况下返回 false。 */
    boolean available();

    /** 开始录制一局。同一 {@code matchId} 重复调用应当幂等。 */
    void startRecording(ReplaySession session);

    /** 结束录制，{@code save} 为 false 表示丢弃这次录制。 */
    void stopRecording(String matchId, boolean save);

    /** 往时间轴上补一个语义标记。 */
    void appendMarker(String matchId, ReplayMarker marker);

    /**
     * 补一批方块变更。
     *
     * <p>这项不能省。包级录像一般只记「玩家自己动手」的方块变化，而灾难破坏地形是插件直接改的，
     * 不补进去录像里地形始终完好——对一个看地形被砸烂的玩法来说等于没录。</p>
     */
    void appendBlockChanges(String matchId, List<BlockChange> changes);

    /** 指定对局的录像是否存在。 */
    boolean exists(String matchId);

    /** 让某个玩家开始观看指定对局的录像。 */
    void play(String matchId, Player viewer);

    /** 删除指定对局的录像。 */
    void delete(String matchId);
}
