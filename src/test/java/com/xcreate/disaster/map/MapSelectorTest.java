package com.xcreate.disaster.map;

import com.xcreate.disaster.config.PluginConfig;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 选图规则的回归。抽图用的是随机数，这里只验证「不该出现的结果不会出现」。 */
class MapSelectorTest {

    private static final List<MapDefinition> POOL = List.of(
            map("alpha"), map("bravo"), map("charlie"));

    @Test
    void 管理员指定的图优先于投票() {
        MapSelector selector = new MapSelector(config("mode: VOTE\nallow-vote: true"));

        var pick = selector.select(POOL, "charlie", Map.of("alpha", 99), Set.of()).orElseThrow();

        assertEquals("charlie", pick.map().id());
        assertEquals(MapSelector.SOURCE_ADMIN, pick.source());
    }

    @Test
    void 票数最高的胜出() {
        MapSelector selector = new MapSelector(config("mode: VOTE\nallow-vote: true"));

        var pick = selector.select(POOL, null, Map.of("alpha", 1, "bravo", 5), Set.of()).orElseThrow();

        assertEquals("bravo", pick.map().id());
        assertEquals(MapSelector.SOURCE_VOTE, pick.source());
    }

    @Test
    void 投票关掉后走配置的模式() {
        MapSelector selector = new MapSelector(config("mode: FIXED\nallow-vote: false\nfixed-map: bravo"));

        var pick = selector.select(POOL, null, Map.of("alpha", 99), Set.of()).orElseThrow();

        assertEquals("bravo", pick.map().id());
        assertEquals(MapSelector.SOURCE_FIXED, pick.source());
    }

    @Test
    void 没人投票时不会卡在投票分支() {
        MapSelector selector = new MapSelector(config("mode: RANDOM\nallow-vote: true"));

        var pick = selector.select(POOL, null, Map.of(), Set.of()).orElseThrow();

        assertEquals(MapSelector.SOURCE_RANDOM, pick.source());
    }

    @Test
    void 连续抽图不会抽到刚用过的那张() {
        MapSelector selector = new MapSelector(config("mode: RANDOM\navoid-repeat: true"));

        String previous = null;
        for (int round = 0; round < 40; round++) {
            String current = selector.select(POOL, null, Map.of(), Set.of()).orElseThrow().map().id();
            assertNotEquals(previous, current, "第 " + round + " 次抽到了刚用过的图");
            previous = current;
        }
    }

    @Test
    void 重置历史后重新开始记() {
        MapSelector selector = new MapSelector(config("mode: RANDOM\navoid-repeat: true"));
        selector.select(POOL, null, Map.of(), Set.of());

        selector.resetHistory();

        assertTrue(selector.recent().isEmpty());
    }

    @Test
    void 轮转模式按顺序走一圈再回到开头() {
        MapSelector selector = new MapSelector(config("mode: ROTATE"));

        assertEquals("alpha", selector.select(POOL, null, Map.of(), Set.of()).orElseThrow().map().id());
        assertEquals("bravo", selector.select(POOL, null, Map.of(), Set.of()).orElseThrow().map().id());
        assertEquals("charlie", selector.select(POOL, null, Map.of(), Set.of()).orElseThrow().map().id());
        assertEquals("alpha", selector.select(POOL, null, Map.of(), Set.of()).orElseThrow().map().id());
    }

    @Test
    void 已被占用的图会让出来() {
        MapSelector selector = new MapSelector(config("mode: RANDOM\navoid-repeat: false"));

        for (int round = 0; round < 20; round++) {
            var pick = selector.select(POOL, null, Map.of(), Set.of("alpha", "bravo")).orElseThrow();
            assertEquals("charlie", pick.map().id());
        }
    }

    @Test
    void 全部被占用时仍然能抽出一张() {
        MapSelector selector = new MapSelector(config("mode: RANDOM"));

        var pick = selector.select(POOL, null, Map.of(), Set.of("alpha", "bravo", "charlie"));

        assertTrue(pick.isPresent(), "宁可连图，也不能开不了局");
    }

    @Test
    void 没有可用地图时返回空() {
        MapSelector selector = new MapSelector(config("mode: RANDOM"));

        assertTrue(selector.select(List.of(), null, Map.of(), Set.of()).isEmpty());
    }

    private static MapDefinition map(String id) {
        return MapDefinition.blank(id, id);
    }

    private static PluginConfig.Maps config(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString("map-selection:\n" + indent(yaml));
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return PluginConfig.parse(configuration).maps();
    }

    private static String indent(String yaml) {
        StringBuilder builder = new StringBuilder();
        for (String line : yaml.split("\n")) {
            builder.append("  ").append(line).append('\n');
        }
        return builder.toString();
    }
}
