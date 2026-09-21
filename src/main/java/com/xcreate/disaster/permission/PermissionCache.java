package com.xcreate.disaster.permission;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 玩家权限快照。
 *
 * <p>对局内每次方块破坏与放置都要问一遍「这人是不是豁免者」，灾难高峰期每 tick 上百次，
 * 每次走一遍平台权限解析不划算。这里按玩家缓存被问过的节点，到期整份作废重算。</p>
 *
 * <p>有缓存就有延迟：服主改完权限最迟要等一个 TTL 才生效。权限插件能上报变更事件的话，
 * 通过 {@link PermissionBridge#watch} 接进来可以立即失效，TTL 就只是兜底。</p>
 *
 * <p>不依赖 Bukkit——权限实际怎么查由调用方以 resolver 传入，因此能脱离服务端测试。</p>
 */
public final class PermissionCache {

    private volatile long ttlMillis;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    /** ttlSeconds 为 0 表示不缓存，每次现查。 */
    public PermissionCache(int ttlSeconds) {
        this.ttlMillis = Math.max(0, ttlSeconds) * 1000L;
    }

    /** 重载配置后改 TTL。已缓存的整份作废，免得旧值按新期限多活一轮。 */
    public void applyTtl(int ttlSeconds) {
        this.ttlMillis = Math.max(0, ttlSeconds) * 1000L;
        invalidateAll();
    }

    /**
     * 查权限。
     *
     * @param resolver 真正的权限查询，只在缓存未命中时调用；返回 null 按 false 处理
     */
    public boolean check(UUID playerId, String node, Function<String, Boolean> resolver) {
        long ttl = ttlMillis;
        if (ttl == 0) {
            return Boolean.TRUE.equals(resolver.apply(node));
        }
        long now = System.currentTimeMillis();
        Snapshot snapshot = snapshots.compute(playerId, (id, current) ->
                current == null || current.expired(now) ? new Snapshot(now + ttl) : current);
        return snapshot.resolve(node, resolver);
    }

    public void invalidate(UUID playerId) {
        snapshots.remove(playerId);
    }

    public void invalidateAll() {
        snapshots.clear();
    }

    /** 正在缓存的玩家数，给调试用。 */
    public int tracked() {
        return snapshots.size();
    }

    private static final class Snapshot {

        private final long expiresAt;
        private final Map<String, Boolean> values = new HashMap<>();

        private Snapshot(long expiresAt) {
            this.expiresAt = expiresAt;
        }

        /** 节点是懒进的：被问过的才缓存，其余不会占位置。 */
        private synchronized boolean resolve(String node, Function<String, Boolean> resolver) {
            Boolean cached = values.get(node);
            if (cached != null) {
                return cached;
            }
            boolean result = Boolean.TRUE.equals(resolver.apply(node));
            values.put(node, result);
            return result;
        }

        private boolean expired(long now) {
            return now >= expiresAt;
        }
    }
}
