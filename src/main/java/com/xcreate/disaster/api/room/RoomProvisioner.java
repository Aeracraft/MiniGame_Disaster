package com.xcreate.disaster.api.room;

import java.util.function.Consumer;

/**
 * 「房间世界从哪来」的契约。
 *
 * <p>本地实现拷一份模板世界；将来的跨服实现会把玩家送到别的服务器上开局，那时同一个接口
 * 里做的是远程调度而不是拷目录。</p>
 *
 * <p>线程约定：{@link #provision} 与 {@link #release} 都在主线程调用，回调也在主线程执行。
 * 实现内部当然可以把耗时的拷贝放到别的线程，但回到主线程之前不许碰世界 API。</p>
 *
 * <p>实现通过 {@code ServicesManager} 注册。项目里查不到实现时一律静默降级，
 * 不因为缺少某个能力就报错停摆。</p>
 */
public interface RoomProvisioner {

    String id();

    boolean available();

    /**
     * 准备一个可用的房间世界。
     *
     * @param callback 结果回调，无论成败都会在主线程被调用一次
     */
    void provision(RoomRequest request, Consumer<ProvisionedRoom> callback);

    /**
     * 回收房间世界。卸载与删目录不要求同步完成，做完后调 {@code whenDone}（在主线程）。
     */
    void release(ProvisionedRoom room, Runnable whenDone);

    /** 关服时调用，释放实现持有的资源。默认什么都不做。 */
    default void shutdown() {
    }
}
