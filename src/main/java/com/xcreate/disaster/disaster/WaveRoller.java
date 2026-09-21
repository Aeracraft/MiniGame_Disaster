package com.xcreate.disaster.disaster;

import com.xcreate.disaster.config.PluginConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * 每波掷骰：必出若干主灾，再按概率决定要不要伴随次灾。
 *
 * <p>{@code random-seed} 固定时整局可复现——排查「这一波怎么砸成这样」时不必靠运气重演。</p>
 *
 * <p>随机源是实例级的，不用 {@code ThreadLocalRandom}：后者取的是全局状态，固定种子对它无效。</p>
 */
public final class WaveRoller {

    private final DisasterRegistry registry;

    private PluginConfig config;
    private Random random;

    public WaveRoller(DisasterRegistry registry, PluginConfig config, Random random) {
        this.registry = registry;
        this.config = config;
        this.random = random;
    }

    /** 重载配置后换一份；配置里换了种子就换随机源，否则保持当前随机序列。 */
    public void apply(PluginConfig config) {
        boolean wasFixed = this.config.disasters().hasFixedSeed();
        this.config = config;
        if (wasFixed != config.disasters().hasFixedSeed()) {
            reseed();
        }
    }

    /** 按当前配置重新取随机源，用于每局开局。 */
    public void reseed() {
        this.random = config.disasters().hasFixedSeed()
                ? new Random(config.disasters().randomSeed())
                : new Random();
    }

    /**
     * 掷一波。
     *
     * @param active 已经激活或本局用过的灾种 id。持续型灾难会一直留在场上，
     *               重复掷中等于白白浪费一波，所以直接从候选池里摘掉
     */
    public DisasterRoll roll(int waveIndex, Collection<String> active) {
        Set<String> excluded = new HashSet<>();
        if (active != null) {
            for (String id : active) {
                if (id != null) {
                    excluded.add(id.toLowerCase(Locale.ROOT));
                }
            }
        }

        List<DisasterDefinition> primaryPool = registry.pool(DisasterTier.PRIMARY, excluded);
        List<DisasterDefinition> primary = pickDistinct(
                primaryPool, Math.max(1, config.game().primaryPerWave()));

        List<DisasterDefinition> secondary = List.of();
        if (random.nextDouble() < config.game().secondaryDisasterChance()) {
            List<DisasterDefinition> secondaryPool = registry.pool(DisasterTier.SECONDARY, excluded);
            secondary = pickDistinct(secondaryPool, 1);
        }

        return new DisasterRoll(waveIndex, primary, secondary);
    }

    /** 不放回地连抽 count 次；池子不够就抽多少算多少。 */
    private List<DisasterDefinition> pickDistinct(List<DisasterDefinition> pool, int count) {
        if (pool.isEmpty() || count <= 0) {
            return List.of();
        }
        List<DisasterDefinition> remaining = new ArrayList<>(pool);
        List<DisasterDefinition> picked = new ArrayList<>(Math.min(count, pool.size()));
        while (picked.size() < count && !remaining.isEmpty()) {
            DisasterDefinition found = weightedPick(remaining);
            picked.add(found);
            remaining.remove(found);
        }
        return picked;
    }

    private DisasterDefinition weightedPick(List<DisasterDefinition> pool) {
        if (pool.size() == 1) {
            return pool.get(0);
        }
        double cap = config.disasters().weightCap();
        double total = 0;
        for (DisasterDefinition definition : pool) {
            total += definition.effectiveWeight(cap);
        }
        if (total <= 0) {
            return pool.get(random.nextInt(pool.size()));
        }
        double roll = random.nextDouble() * total;
        double accumulated = 0;
        for (DisasterDefinition definition : pool) {
            accumulated += definition.effectiveWeight(cap);
            if (roll < accumulated) {
                return definition;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
