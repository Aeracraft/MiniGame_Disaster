/**
 * 回放与外部上报的预埋契约。
 *
 * <h2>一期的对外承诺只有两样东西</h2>
 * <ol>
 *   <li><b>语义事件</b>（在 {@code com.xcreate.disaster.api.event}）：
 *       {@code DisasterMatchStartedEvent}、{@code DisasterDisasterSpawnedEvent}、
 *       {@code DisasterPlayerEliminatedEvent}、{@code DisasterBlockChangedEvent}、
 *       {@code DisasterMatchEndedEvent}，全部带 {@code matchId}。
 *       任何插件监听这些事件即可自行录像或上报。</li>
 *   <li><b>{@link com.xcreate.disaster.api.replay.ReplayProvider} 接口</b>：
 *       回放引擎的实现契约。</li>
 * </ol>
 *
 * <h2>角色分工（重要）</h2>
 * 玩法插件只推「<b>事实层</b>」数据——中立地描述游戏里发生了什么，
 * 不含任何平台知识（不认识论坛、不认识网页、不认识任何具体回放引擎）。
 * 一切翻译工作由消费端负责。这样将来新增对接（网页、机器人、论坛扩展）
 * 都从同一份事实数据出发，<b>玩法插件永远不需要为此改动</b>。
 *
 * <h2>一期明确不做</h2>
 * 不集成任何现成回放引擎、不写数据包注入、不做录像观看入口、不做战报导出。
 *
 * @since 1.0
 */
package com.xcreate.disaster.api.replay;
