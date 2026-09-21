/**
 * 回放与外部上报的预埋契约。
 *
 * <p>一期对外只承诺两样东西：语义事件（都在 {@code com.xcreate.disaster.api.event}，
 * 全部带 {@code matchId}，监听它们就能自行录像或上报），以及
 * {@link com.xcreate.disaster.api.replay.ReplayProvider} 这个回放引擎实现契约。</p>
 *
 * <p>玩法插件只推「事实层」数据——中立描述游戏里发生了什么，不含任何平台知识，
 * 不认识论坛、网页，也不认识任何具体回放引擎。翻译工作全交给消费端，这样以后新增
 * 对接（网页、机器人、论坛扩展）都从同一份事实数据出发，插件不用跟着改。</p>
 *
 * <p>一期不做：集成现成回放引擎、数据包注入、录像观看入口、战报导出。</p>
 */
package com.xcreate.disaster.api.replay;
