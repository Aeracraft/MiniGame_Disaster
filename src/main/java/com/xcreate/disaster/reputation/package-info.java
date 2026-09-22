/**
 * 对局评价与玩家互评。
 *
 * <p>分工：这个包管游戏侧的两道闸（评价时间窗、参与者校验）与整套编排；落库与其余防刷策略
 * 归 {@link com.xcreate.disaster.api.reputation.ReputationProvider} 的实现。</p>
 *
 * <p>窗口登记 {@link com.xcreate.disaster.reputation.RatingWindow} 与各道闸的判定
 * {@link com.xcreate.disaster.reputation.RatingGuard} 都是纯逻辑，不依赖服务端。</p>
 */
package com.xcreate.disaster.reputation;
