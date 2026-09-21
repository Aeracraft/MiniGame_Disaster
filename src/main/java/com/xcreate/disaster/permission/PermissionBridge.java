package com.xcreate.disaster.permission;

/**
 * 可选接入的权限插件。
 *
 * <p>Bukkit 的 {@code hasPermission} 本来就兼容所有权限插件，所以这个接口不负责查权限，
 * 只提供插件独有的部分。目前只有一个用途：把权限变更事件报过来，让
 * {@link PermissionCache} 立即失效。没有实现时缓存退回纯 TTL 过期，功能不受影响。</p>
 *
 * <p>实现由 {@code ServicesManager} 注册，插件侧只查服务、不认识任何具体实现。
 * 依赖方向单向：接口在这里，实现方 {@code compileOnly} 依赖本插件。</p>
 */
public interface PermissionBridge {

    String id();

    boolean available();

    /**
     * 订阅权限变更。
     *
     * @param onChanged 变更时回调，可能在异步线程
     * @return 是否订阅成功；false 表示调用方应当退回 TTL 兜底
     */
    boolean watch(Runnable onChanged);
}
