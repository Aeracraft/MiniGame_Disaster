package com.xcreate.disaster.api.storage;

/**
 * 一局打完后外加到玩家累积统计上的增量。
 *
 * <p>传增量而不是快照是刻意为之：多台子服可能同时结算不同对局的同一个玩家，增量交给数据库
 * 原子累加就不需要任何锁，写快照则会互相覆盖静默丢数据。</p>
 */
public record PlayerDelta(int matches, int wins, int deaths, long survivalSeconds) {

    /**
     * 从一局的结果构造增量。
     *
     * @param survived        活到对局结束
     * @param countedAsWin    是否计入获胜（挂机者存活也不算赢）
     */
    public static PlayerDelta ofMatch(boolean survived, boolean countedAsWin, long survivalSeconds) {
        return new PlayerDelta(
                1,
                countedAsWin ? 1 : 0,
                survived ? 0 : 1,
                Math.max(0L, survivalSeconds));
    }
}
