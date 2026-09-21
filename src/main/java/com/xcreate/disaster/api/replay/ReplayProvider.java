package com.xcreate.disaster.api.replay;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * 回放引擎的可插拔契约。
 *
 * <h2>为什么是「预埋」而不是「实现」</h2>
 * 一期内置实现为空：Disaster 只负责<b>定义</b>这个接口并把语义事件广播出去，
 * 不提供任何录像能力。第三方（或后续自研的）回放插件实现本接口后注册到
 * {@code ServicesManager} 即可接管。
 *
 * <h2>依赖方向</h2>
 * 本接口定义在 Disaster 侧，回放插件 {@code compileOnly} 依赖 Disaster，
 * <b>Disaster 不依赖回放插件</b>。Disaster 只做
 * {@code ServicesManager.getRegistration(ReplayProvider.class)} 查询，
 * 查不到就静默跳过全部回放相关行为——绝不能因为回放插件缺失而报错。
 *
 * <h2>实现约定</h2>
 * <ul>
 *   <li>所有方法都可能在主线程被调用，<b>实现方必须自行异步化</b>，
 *       不得在方法内做阻塞 I/O。</li>
 *   <li>实现方负责处理录像与世界的生命周期关系：房间世界打完结会被删除，
 *       而录像里烧死着世界名，因此实现方需要在加载录像时做世界名重映射。</li>
 * </ul>
 */
public interface ReplayProvider {

    /** 实现标识，例如 {@code advancedreplay} / {@code native}。 */
    String id();

    /** 当前是否可正常工作（依赖缺失、存储不可用等情况下返回 false）。 */
    boolean available();

    /**
     * 开始录制一局。重复调用同一 {@code matchId} 应视为幂等。
     */
    void startRecording(ReplaySession session);

    /**
     * 结束录制。
     *
     * @param save 是否保存。传 false 表示丢弃这次录制
     */
    void stopRecording(String matchId, boolean save);

    /** 往时间轴上补一个语义标记。 */
    void appendMarker(String matchId, ReplayMarker marker);

    /**
     * 补一批方块变更。
     *
     * <p>这一项是必需的：包级录像通常只记录「玩家自己动手」的方块变化，
     * 而灾难对地形的破坏是插件直接改的，不补进去录像里就会地形完好——
     * 对一个以「看地形被砸烂」为核心的玩法来说等于没录。</p>
     */
    void appendBlockChanges(String matchId, List<BlockChange> changes);

    /** 指定对局的录像是否存在。 */
    boolean exists(String matchId);

    /** 让某个玩家开始观看指定对局的录像。 */
    void play(String matchId, Player viewer);

    /** 删除指定对局的录像。 */
    void delete(String matchId);
}
