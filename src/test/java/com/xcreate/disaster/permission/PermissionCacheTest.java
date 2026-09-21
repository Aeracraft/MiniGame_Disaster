package com.xcreate.disaster.permission;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCacheTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String NODE = "disaster.bypass.protection";

    private static Function<String, Boolean> counting(AtomicInteger calls, boolean result) {
        return node -> {
            calls.incrementAndGet();
            return result;
        };
    }

    @Test
    void 同一节点只问一次() {
        PermissionCache cache = new PermissionCache(60);
        AtomicInteger calls = new AtomicInteger();
        Function<String, Boolean> resolver = counting(calls, true);

        for (int i = 0; i < 20; i++) {
            assertTrue(cache.check(ALICE, NODE, resolver));
        }
        assertEquals(1, calls.get());
    }

    @Test
    void 不同节点分别缓存() {
        PermissionCache cache = new PermissionCache(60);
        AtomicInteger calls = new AtomicInteger();
        Function<String, Boolean> resolver = node -> {
            calls.incrementAndGet();
            return node.endsWith("protection");
        };

        assertTrue(cache.check(ALICE, NODE, resolver));
        assertFalse(cache.check(ALICE, "disaster.admin.map", resolver));
        assertEquals(2, calls.get());

        assertTrue(cache.check(ALICE, NODE, resolver));
        assertEquals(2, calls.get());
    }

    @Test
    void 玩家之间互不串用() {
        PermissionCache cache = new PermissionCache(60);

        assertTrue(cache.check(ALICE, NODE, node -> true));
        assertFalse(cache.check(BOB, NODE, node -> false));
        assertEquals(2, cache.tracked());
    }

    @Test
    void 关掉缓存后每次都现查() {
        PermissionCache cache = new PermissionCache(0);
        AtomicInteger calls = new AtomicInteger();
        Function<String, Boolean> resolver = counting(calls, true);

        cache.check(ALICE, NODE, resolver);
        cache.check(ALICE, NODE, resolver);
        assertEquals(2, calls.get());
        assertEquals(0, cache.tracked());
    }

    @Test
    void 单个失效之后重新查() {
        PermissionCache cache = new PermissionCache(60);
        AtomicInteger calls = new AtomicInteger();
        Function<String, Boolean> resolver = counting(calls, true);

        cache.check(ALICE, NODE, resolver);
        cache.invalidate(ALICE);
        cache.check(ALICE, NODE, resolver);
        assertEquals(2, calls.get());
    }

    @Test
    void 全部失效之后重新查() {
        PermissionCache cache = new PermissionCache(60);
        AtomicInteger calls = new AtomicInteger();
        Function<String, Boolean> resolver = counting(calls, true);

        cache.check(ALICE, NODE, resolver);
        cache.check(BOB, NODE, resolver);
        cache.invalidateAll();
        assertEquals(0, cache.tracked());

        cache.check(ALICE, NODE, resolver);
        assertEquals(3, calls.get());
    }

    @Test
    void 改有效期会清掉旧值() {
        PermissionCache cache = new PermissionCache(60);
        AtomicInteger calls = new AtomicInteger();
        Function<String, Boolean> resolver = counting(calls, true);

        cache.check(ALICE, NODE, resolver);
        cache.applyTtl(0);
        cache.check(ALICE, NODE, resolver);
        assertEquals(2, calls.get());
    }

    @Test
    void 过期之后重新查() throws InterruptedException {
        PermissionCache cache = new PermissionCache(1);
        AtomicInteger calls = new AtomicInteger();
        Function<String, Boolean> resolver = counting(calls, true);

        cache.check(ALICE, NODE, resolver);
        Thread.sleep(1100L);
        cache.check(ALICE, NODE, resolver);
        assertEquals(2, calls.get());
    }

    @Test
    void 查询返回空按拒绝处理() {
        PermissionCache cache = new PermissionCache(60);
        assertFalse(cache.check(ALICE, NODE, node -> null));
    }
}
