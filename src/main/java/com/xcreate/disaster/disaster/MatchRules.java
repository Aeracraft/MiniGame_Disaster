package com.xcreate.disaster.disaster;

/**
 * 灾难改得动的对局规则。
 *
 * <p>混战改的不是地形而是规则，改完由对局循环读走。放在灾难包里是因为改规则与改地形
 * 在效果层属于同一件事——都是「灾种对环境做了什么」；反过来把对局对象交给灾种，
 * 灾难包就得依赖对局包，两边会绕成环。</p>
 */
public final class MatchRules {

    private boolean pvp;
    private boolean halfSurvivorsEnd;

    /** 允许玩家互相伤害。开局时是关的，混战掷中后打开。 */
    public boolean pvp() {
        return pvp;
    }

    public void pvp(boolean value) {
        this.pvp = value;
    }

    /** 存活人数掉到开局的一半就结束，不等时间到。 */
    public boolean halfSurvivorsEnd() {
        return halfSurvivorsEnd;
    }

    public void halfSurvivorsEnd(boolean value) {
        this.halfSurvivorsEnd = value;
    }
}
