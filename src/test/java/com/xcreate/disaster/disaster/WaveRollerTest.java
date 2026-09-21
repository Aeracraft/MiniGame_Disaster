package com.xcreate.disaster.disaster;

import com.xcreate.disaster.config.PluginConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 掷骰规则的回归：分层、概率、排除、上限，以及固定种子的可复现性。 */
class WaveRollerTest {

    private static final Logger LOGGER = Logger.getLogger("disaster-test");

    @TempDir
    Path folder;

    @Test
    void 主灾与次灾各抽各的() {
        DisasterRegistry registry = registry(Map.of(
                "alpha", "tier: PRIMARY",
                "bravo", "tier: PRIMARY",
                "side", "tier: SECONDARY"));

        WaveRoller roller = roller(registry, "game.secondary-disaster-chance=1.0");

        for (int round = 0; round < 30; round++) {
            DisasterRoll roll = roller.roll(1, List.of());

            assertEquals(1, roll.primary().size());
            assertEquals(DisasterTier.PRIMARY, roll.primary().get(0).tier());
            assertEquals(1, roll.secondary().size());
            assertEquals("side", roll.secondary().get(0).id(), "次灾池里只有这一个");
        }
    }

    @Test
    void 次灾概率为零时永远不出次灾() {
        DisasterRegistry registry = registry(Map.of(
                "alpha", "tier: PRIMARY",
                "side", "tier: SECONDARY"));

        WaveRoller roller = roller(registry, "game.secondary-disaster-chance=0");

        for (int round = 0; round < 30; round++) {
            assertFalse(roller.roll(1, List.of()).hasSecondary());
        }
    }

    @Test
    void 已经激活的灾种不会再被掷中() {
        DisasterRegistry registry = registry(Map.of(
                "alpha", "tier: PRIMARY",
                "bravo", "tier: PRIMARY"));

        WaveRoller roller = roller(registry, "game.secondary-disaster-chance=0");

        for (int round = 0; round < 20; round++) {
            assertEquals("bravo", roller.roll(1, List.of("alpha")).primary().get(0).id());
        }
    }

    @Test
    void 禁用与零权重的灾种不进候选池() {
        DisasterRegistry registry = registry(Map.of(
                "alpha", "tier: PRIMARY",
                "off", "tier: PRIMARY\nenabled: false",
                "zero", "tier: PRIMARY\nweight: 0"));

        WaveRoller roller = roller(registry);

        for (int round = 0; round < 20; round++) {
            assertEquals("alpha", roller.roll(1, List.of()).primary().get(0).id());
        }
    }

    @Test
    void 候选池被摘空时返回空波次() {
        DisasterRegistry registry = registry(Map.of("alpha", "tier: PRIMARY"));

        DisasterRoll roll = roller(registry, "game.secondary-disaster-chance=0")
                .roll(3, List.of("alpha"));

        assertTrue(roll.isEmpty(), "宁可这一波什么都不放，也不重复砸同一个");
        assertEquals(3, roll.waveIndex());
    }

    @Test
    void 固定种子下同一串随机数复现同一局() {
        DisasterRegistry registry = registry(Map.of(
                "alpha", "tier: PRIMARY",
                "bravo", "tier: PRIMARY",
                "charlie", "tier: PRIMARY",
                "side", "tier: SECONDARY"));

        assertEquals(sequence(registry), sequence(registry));
    }

    @Test
    void 权重高的更容易被抽中() {
        DisasterRegistry registry = registry(Map.of(
                "heavy", "tier: PRIMARY\nweight: 10",
                "light", "tier: PRIMARY\nweight: 1"));

        // 上限设成不限，否则 10 会被截断到 1.75，看不出差距
        WaveRoller roller = roller(registry, "disaster.weight-cap=0");
        int heavy = 0;
        for (int round = 0; round < 400; round++) {
            if (roller.roll(1, List.of()).primary().get(0).id().equals("heavy")) {
                heavy++;
            }
        }

        assertTrue(heavy > 300, "权重 10:1 时重的那边该占绝大多数，实际 " + heavy + "/400");
    }

    @Test
    void 权重上限把畸高的权重压回来() {
        DisasterRegistry registry = registry(Map.of(
                "heavy", "tier: PRIMARY\nweight: 10",
                "light", "tier: PRIMARY\nweight: 1.75"));

        WaveRoller roller = roller(registry, "disaster.weight-cap=1.75");
        int heavy = 0;
        for (int round = 0; round < 400; round++) {
            if (roller.roll(1, List.of()).primary().get(0).id().equals("heavy")) {
                heavy++;
            }
        }

        assertTrue(heavy < 300, "压到同一档后不该再一边倒，实际 " + heavy + "/400");
        assertTrue(heavy > 100, "也不该反过来一边倒，实际 " + heavy + "/400");
    }

    @Test
    void 每波多个主灾时互不重复() {
        DisasterRegistry registry = registry(Map.of(
                "alpha", "tier: PRIMARY",
                "bravo", "tier: PRIMARY",
                "charlie", "tier: PRIMARY"));

        DisasterRoll roll = roller(registry,
                "game.primary-per-wave=3", "game.secondary-disaster-chance=0").roll(1, List.of());

        assertEquals(3, roll.primary().size());
        assertEquals(3, roll.ids().stream().distinct().count());
    }

    @Test
    void 主灾数量超过池子大小时按池子给() {
        DisasterRegistry registry = registry(Map.of(
                "alpha", "tier: PRIMARY",
                "bravo", "tier: PRIMARY"));

        DisasterRoll roll = roller(registry,
                "game.primary-per-wave=5", "game.secondary-disaster-chance=0").roll(1, List.of());

        assertEquals(2, roll.primary().size());
    }

    private List<String> sequence(DisasterRegistry registry) {
        WaveRoller roller = roller(registry, "game.secondary-disaster-chance=1.0");
        List<String> ids = new ArrayList<>();
        List<String> active = new ArrayList<>();
        for (int wave = 1; wave <= 8; wave++) {
            DisasterRoll roll = roller.roll(wave, active);
            ids.addAll(roll.ids());
            active.addAll(roll.ids());
        }
        return ids;
    }

    private WaveRoller roller(DisasterRegistry registry, String... overrides) {
        return new WaveRoller(registry, config(overrides), new Random(20260921L));
    }

    private DisasterRegistry registry(Map<String, String> bodies) {
        File dir = folder.toFile();
        for (Map.Entry<String, String> entry : bodies.entrySet()) {
            write(dir, entry.getKey() + ".yml",
                    "id: " + entry.getKey() + "\n" + entry.getValue() + "\n");
        }
        DisasterRegistry registry = new DisasterRegistry(dir, LOGGER);
        registry.reload();
        return registry;
    }

    private static void write(File dir, String name, String body) {
        try {
            Files.writeString(new File(dir, name).toPath(), body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 用点分键覆写，省得在测试里拼缩进。 */
    private static PluginConfig config(String... keyValues) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("game.primary-per-wave", 1);
        yaml.set("game.secondary-disaster-chance", 0.5);
        yaml.set("disaster.weight-cap", 1.75);
        yaml.set("disaster.spawn-validation.max-retries", 20);
        for (String pair : keyValues) {
            int split = pair.indexOf('=');
            if (split <= 0) {
                continue;
            }
            yaml.set(pair.substring(0, split), value(pair.substring(split + 1)));
        }
        return PluginConfig.parse(yaml);
    }

    private static Object value(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(raw);
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            // 落到小数
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }
}
