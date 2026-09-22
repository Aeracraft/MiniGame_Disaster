package com.xcreate.disaster.api.reputation;

/**
 * 一次评价提交的受理结果。
 *
 * <p>每种拒绝都带一句能直接说给玩家听的说明，省得调用方自己拼文案。</p>
 */
public enum RatingVerdict {

    ACCEPTED("已记录，谢谢反馈"),
    DISABLED("评价功能未开启"),
    WINDOW_CLOSED("评价时间已过"),
    NOT_PARTICIPANT("你不在这一局里"),
    SELF("不能给自己点赞"),
    ALREADY_SUBMITTED("这一局你已经评过了"),
    ALREADY_RECOMMENDED("这一局你已经给这位玩家点过赞了"),
    COOLDOWN("和这位玩家前不久刚互评过，过阵子再来"),
    LIMIT_REACHED("本局点赞数已到上限"),
    INVALID("评价内容不合法"),
    STORAGE_UNAVAILABLE("数据存储不可用，暂时收不了评价");

    private final String label;

    RatingVerdict(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean accepted() {
        return this == ACCEPTED;
    }
}
