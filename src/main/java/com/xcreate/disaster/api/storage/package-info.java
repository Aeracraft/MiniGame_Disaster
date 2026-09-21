/**
 * 存储层契约。
 *
 * <p>所有方法都返回 {@link java.util.concurrent.CompletableFuture}：实现里既有磁盘 I/O 也有
 * JDBC，都不允许出现在主线程 tick 路径上。调用方拿到 future 后自己决定要不要回主线程。</p>
 *
 * <p>累积统计传的是增量不是快照。一局对局的归属是唯一的，多台子服不会同时结算同一局，
 * 让存储做原子累加就够了，不需要分布式锁；写快照在并发下会静默丢数据。</p>
 */
package com.xcreate.disaster.api.storage;
