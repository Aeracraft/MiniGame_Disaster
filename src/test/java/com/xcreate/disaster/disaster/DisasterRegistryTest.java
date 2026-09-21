package com.xcreate.disaster.disaster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 灾种文件格式的回归。
 *
 * <p>这些文件是给服主手改的，格式写歪编译期一点看不出来，只会在别人机器上炸，
 * 所以「写出能读回来」「缺键兜得住」「文件名说了算」都得钉住。</p>
 */
class DisasterRegistryTest {

    private static final Logger LOGGER = Logger.getLogger("disaster-test");

    @TempDir
    Path folder;

    @Test
    void 全字段都能读回来() {
        write("meteor_shower.yml", """
                id: meteor_shower
                display-name: 流星雨
                tier: PRIMARY
                enabled: true
                weight: 1.5
                spawn-strategy: AROUND_EACH_PLAYER
                point-count: 4
                options:
                  radius: 12
                """);

        DisasterDefinition definition = registry().find("meteor_shower").orElseThrow();

        assertEquals("meteor_shower", definition.id());
        assertEquals("流星雨", definition.displayName());
        assertEquals(DisasterTier.PRIMARY, definition.tier());
        assertTrue(definition.enabled());
        assertEquals(1.5, definition.weight());
        assertEquals(SpawnStrategy.AROUND_EACH_PLAYER, definition.strategy());
        assertEquals(4, definition.pointCount());
        assertEquals(12, definition.options().getInt("radius", 0));
        assertTrue(definition.producesPoints());
    }

    @Test
    void 下划线开头的文件不加载() {
        write("_example.yml", "id: example\ntier: PRIMARY\n");
        write("real.yml", "tier: PRIMARY\n");

        DisasterRegistry registry = registry();

        assertEquals(1, registry.all().size());
        assertTrue(registry.find("real").isPresent());
        assertTrue(registry.find("_example").isEmpty());
        assertFalse(registry.find("example").isPresent());
    }

    @Test
    void 层级与策略写错时退回默认值() {
        write("weird.yml", "tier: 什么\nspawn-strategy: 乱写\n");

        DisasterDefinition definition = registry().find("weird").orElseThrow();

        assertEquals(DisasterTier.PRIMARY, definition.tier());
        assertEquals(SpawnStrategy.RANDOM_IN_BOUNDS, definition.strategy());
    }

    @Test
    void 数值写错时退回默认值() {
        write("broken.yml", "weight: 不是数字\npoint-count: 很多\n");

        DisasterDefinition definition = registry().find("broken").orElseThrow();

        assertEquals(1.0, definition.weight());
        assertEquals(0, definition.pointCount());
        assertFalse(definition.producesPoints());
    }

    @Test
    void 文件名决定id() {
        write("real.yml", "id: 别的名字\ntier: PRIMARY\n");

        DisasterRegistry registry = registry();

        assertTrue(registry.find("real").isPresent());
        assertTrue(registry.find("别的名字").isEmpty());
    }

    @Test
    void 缺字段时取默认值() {
        write("bare.yml", "");

        DisasterDefinition definition = registry().find("bare").orElseThrow();

        assertEquals("bare", definition.displayName(), "没写展示名就用 id");
        assertTrue(definition.enabled());
        assertEquals(1.0, definition.weight());
        assertEquals(SpawnStrategy.RANDOM_IN_BOUNDS, definition.strategy());
        assertEquals(0, definition.pointCount());
        assertTrue(definition.options().isEmpty());
    }

    @Test
    void id查询不区分大小写() {
        write("meteor_shower.yml", "tier: PRIMARY\n");

        DisasterRegistry registry = registry();

        assertTrue(registry.find("METEOR_SHOWER").isPresent());
        assertTrue(registry.find("Meteor_Shower").isPresent());
    }

    @Test
    void 按层级分开列表() {
        write("a.yml", "tier: PRIMARY\n");
        write("b.yml", "tier: PRIMARY\n");
        write("c.yml", "tier: SECONDARY\n");

        DisasterRegistry registry = registry();

        assertEquals(2, registry.byTier(DisasterTier.PRIMARY).size());
        assertEquals(1, registry.byTier(DisasterTier.SECONDARY).size());
        assertEquals("c", registry.byTier(DisasterTier.SECONDARY).get(0).id());
    }

    @Test
    void 候选池会摘掉指定的灾种() {
        write("a.yml", "tier: PRIMARY\n");
        write("b.yml", "tier: PRIMARY\nenabled: false\n");
        write("c.yml", "tier: PRIMARY\nweight: 0\n");

        DisasterRegistry registry = registry();
        List<String> ids = registry.pool(DisasterTier.PRIMARY, Set.of())
                .stream().map(DisasterDefinition::id).toList();

        assertEquals(List.of("a"), ids, "被禁用的与零权重的都不该进池");
        assertTrue(registry.pool(DisasterTier.PRIMARY, Set.of("A")).isEmpty());
    }

    @Test
    void 写出去的定义能读回来() throws IOException {
        write("sample.yml", "tier: SECONDARY\n");
        DisasterRegistry registry = registry();
        DisasterDefinition original = registry.find("sample").orElseThrow();

        DisasterDefinition changed = new DisasterDefinition(
                original.id(), "示例", DisasterTier.PRIMARY, false, 0.75,
                SpawnStrategy.FROM_EDGE, 3, DisasterOptions.of(Map.of("edge-width", 12)));
        registry.save(changed);

        DisasterDefinition reloaded = registry().find("sample").orElseThrow();

        assertEquals("示例", reloaded.displayName());
        assertEquals(DisasterTier.PRIMARY, reloaded.tier());
        assertFalse(reloaded.enabled());
        assertEquals(0.75, reloaded.weight());
        assertEquals(SpawnStrategy.FROM_EDGE, reloaded.strategy());
        assertEquals(3, reloaded.pointCount());
        assertEquals(12, reloaded.options().getInt("edge-width", 0));
    }

    @Test
    void 一个文件写坏不影响其他文件() {
        write("good.yml", "tier: PRIMARY\n");
        write("bad.yml", "tier: [未闭合\n");

        DisasterRegistry registry = registry();

        assertTrue(registry.find("good").isPresent(), "坏文件不该拖累好文件");
    }

    @Test
    void 参数表的取值兜得住错的类型() {
        write("opts.yml", """
                tier: PRIMARY
                spawn-strategy: NEAR_PLAYER
                point-count: 2
                options:
                  radius: "10"
                  edge-width: 不是数字
                """);

        DisasterOptions options = registry().find("opts").orElseThrow().options();

        assertEquals(10, options.getInt("radius", 0), "字符串数字也该认");
        assertEquals(8, options.getInt("edge-width", 8), "读不动就退回默认");
        assertEquals(8, options.getInt("不存在", 8));
    }

    @Test
    void 权重按上限截断() {
        DisasterDefinition heavy = new DisasterDefinition("x", "x", DisasterTier.PRIMARY, true,
                10.0, SpawnStrategy.RANDOM_IN_BOUNDS, 1, DisasterOptions.empty());
        DisasterDefinition disabled = new DisasterDefinition("y", "y", DisasterTier.PRIMARY, false,
                1.0, SpawnStrategy.RANDOM_IN_BOUNDS, 1, DisasterOptions.empty());

        assertTrue(heavy.rolls());
        assertEquals(1.75, heavy.effectiveWeight(1.75));
        assertEquals(10.0, heavy.effectiveWeight(0), "上限为 0 表示不限制");
        assertFalse(disabled.rolls(), "关掉的灾种不参与掷骰");
    }

    private DisasterRegistry registry() {
        DisasterRegistry registry = new DisasterRegistry(folder.toFile(), LOGGER);
        registry.reload();
        return registry;
    }

    private void write(String name, String body) {
        try {
            Files.writeString(new File(folder.toFile(), name).toPath(), body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
