package com.xcreate.disaster.disaster;

/**
 * 会一直作用下去的那部分灾难。
 *
 * <p>洪水涨水、龙卷风推进、脚下岩浆这类掷中一次要持续到本局结束，所以效果层分两截：
 * 灾种先给出一个活动实例，之后由对局循环每秒推一次。</p>
 *
 * <p>状态存在实例里，一个实例只服务一局。灾种效果本身是全服共用的单例，把对局状态挂上去
 * 会让同时开着的几个房间互相串。</p>
 */
public interface ActiveDisaster {

    /**
     * 推进一次。对局循环每秒调一次，所以这里的「一步」就是一秒。
     *
     * @param elapsedSeconds 本局已经过去的秒数
     * @return false 表示已经跑完，可以从活动列表里摘掉
     */
    boolean tick(EffectContext context, int elapsedSeconds);
}
