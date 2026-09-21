package com.xcreate.disaster.room;

/**
 * 加入请求的结果。
 *
 * <p>{@link Status#CREATING} 时玩家已经算在房间里了，只是世界还在拷，等他几秒就能落地。</p>
 */
public record JoinOutcome(Status status, Room room, String detail) {

    public enum Status {
        /** 已进入现成的房间。 */
        JOINED,
        /** 正在为他新建房间，世界准备好后会把他传进去。 */
        CREATING,
        /** 房间满了，已排进等待队列。 */
        QUEUED,
        /** 他本来就在某个房间里。 */
        ALREADY_IN,
        /** 进不去，原因在 {@link JoinOutcome#detail()} 里。 */
        FAILED
    }

    public static JoinOutcome joined(Room room) {
        return new JoinOutcome(Status.JOINED, room, "");
    }

    public static JoinOutcome creating(Room room) {
        return new JoinOutcome(Status.CREATING, room, "");
    }

    public static JoinOutcome alreadyIn(Room room) {
        return new JoinOutcome(Status.ALREADY_IN, room, "");
    }

    public static JoinOutcome queued(int position) {
        return new JoinOutcome(Status.QUEUED, null, String.valueOf(position));
    }

    public static JoinOutcome failed(String reason) {
        return new JoinOutcome(Status.FAILED, null, reason);
    }

    public boolean success() {
        return status == Status.JOINED || status == Status.CREATING
                || status == Status.ALREADY_IN || status == Status.QUEUED;
    }
}
