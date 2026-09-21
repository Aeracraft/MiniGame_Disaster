package com.xcreate.disaster.permission;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 权限查询入口：缓存加可选的权限插件桥接。
 *
 * <p>玩法侧的权限检查统一走这里。直接调 {@code player.hasPermission} 也能工作，
 * 但高频路径会绕开缓存。</p>
 */
public final class PermissionService {

    private final PermissionCache cache;
    private final PermissionBridge bridge;
    private final TitleProvider titles;

    /**
     * @param bridge 可为 null，或返回 {@code available() == false}
     * @param titles 同上
     */
    public PermissionService(PermissionCache cache, PermissionBridge bridge, TitleProvider titles) {
        this.cache = cache;
        this.bridge = bridge != null && bridge.available() ? bridge : null;
        this.titles = titles != null && titles.available() ? titles : null;

        // 桥接能上报变更的话，缓存就不必等 TTL 自然过期
        if (this.bridge != null) {
            this.bridge.watch(cache::invalidateAll);
        }
    }

    public boolean has(Player player, String node) {
        return cache.check(player.getUniqueId(), node, player::hasPermission);
    }

    /** 玩家退出或权限变更后清掉，避免旧值滞留。 */
    public void forget(UUID playerId) {
        cache.invalidate(playerId);
    }

    public void forgetAll() {
        cache.invalidateAll();
    }

    /** 重载配置后改缓存有效期。 */
    public void applyTtl(int ttlSeconds) {
        cache.applyTtl(ttlSeconds);
    }

    /** 没有接入称号来源时返回空串。 */
    public String prefix(UUID playerId) {
        if (titles == null) {
            return "";
        }
        String prefix = titles.prefix(playerId);
        return prefix == null ? "" : prefix;
    }

    /** 接入的权限插件 id，没接时为空串。 */
    public String bridgeId() {
        return bridge == null ? "" : bridge.id();
    }

    public String titleProviderId() {
        return titles == null ? "" : titles.id();
    }

    public int cachedPlayers() {
        return cache.tracked();
    }
}
