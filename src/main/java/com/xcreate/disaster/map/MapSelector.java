package com.xcreate.disaster.map;

import com.xcreate.disaster.config.MapMode;
import com.xcreate.disaster.config.PluginConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 从可开局的地图里挑一张。
 *
 * <p>优先级固定为「管理员指定 &gt; 投票 &gt; 配置的模式」。投票只在配置允许、且确实有人投票时
 * 才插手，否则一路走到模式分支。</p>
 *
 * <p>已经开着的房间占用的图会先被排除掉，避免同一张图同时跑两局；万一可用图只剩被占用的，
 * 就放弃排除而不是放弃开局。</p>
 */
public final class MapSelector {

    /** 抽图结果的来源，会进日志与上报数据，方便回头查「这局怎么选的图」。 */
    public static final String SOURCE_ADMIN = "admin";
    public static final String SOURCE_VOTE = "vote";
    public static final String SOURCE_RANDOM = "random";
    public static final String SOURCE_ROTATE = "rotate";
    public static final String SOURCE_FIXED = "fixed";

    public record Pick(MapDefinition map, String source) {
    }

    private PluginConfig.Maps config;

    /** 最近用过的地图 id，队首最旧。只在内存里，重启即清空。 */
    private final Deque<String> recent = new ArrayDeque<>();

    private int rotateIndex;

    public MapSelector(PluginConfig.Maps config) {
        this.config = config;
    }

    /** 重载配置后换一份，最近用过的图不清空。 */
    public void apply(PluginConfig.Maps config) {
        this.config = config;
    }

    /**
     * 挑一张图。
     *
     * @param pool        可开局的地图
     * @param adminChoice 管理员指定的地图 id，没有则传 null
     * @param votes       玩家投票的「地图 id 到票数」，没有则传空
     * @param excluded    当前已被占用、应当避开的图 id
     */
    public Optional<Pick> select(List<MapDefinition> pool, String adminChoice,
                                 Map<String, Integer> votes, Set<String> excluded) {
        if (pool == null || pool.isEmpty()) {
            return Optional.empty();
        }

        List<MapDefinition> candidates = exclude(pool, excluded);

        if (adminChoice != null && !adminChoice.isBlank()) {
            Optional<MapDefinition> picked = findById(candidates, adminChoice);
            if (picked.isPresent()) {
                return Optional.of(accept(picked.get(), SOURCE_ADMIN));
            }
        }

        if (config.allowVote() && votes != null && !votes.isEmpty()) {
            Optional<MapDefinition> picked = byVote(candidates, votes);
            if (picked.isPresent()) {
                return Optional.of(accept(picked.get(), SOURCE_VOTE));
            }
        }

        return switch (config.mode()) {
            case FIXED -> findById(candidates, config.fixedMap())
                    .map(map -> accept(map, SOURCE_FIXED));
            case ROTATE -> Optional.of(accept(byRotation(candidates), SOURCE_ROTATE));
            default -> Optional.of(accept(avoidRepeat(candidates), SOURCE_RANDOM));
        };
    }

    /** 最近用过的地图 id，最新的在末尾。 */
    public List<String> recent() {
        return List.copyOf(recent);
    }

    public void resetHistory() {
        recent.clear();
        rotateIndex = 0;
    }

    private List<MapDefinition> exclude(List<MapDefinition> pool, Set<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            return pool;
        }
        Set<String> keys = new HashSet<>();
        for (String id : excluded) {
            if (id != null) {
                keys.add(id.toLowerCase(Locale.ROOT));
            }
        }
        List<MapDefinition> kept = new ArrayList<>(pool.size());
        for (MapDefinition map : pool) {
            if (!keys.contains(map.id().toLowerCase(Locale.ROOT))) {
                kept.add(map);
            }
        }
        return kept.isEmpty() ? pool : kept;
    }

    private static Optional<MapDefinition> findById(List<MapDefinition> pool, String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        for (MapDefinition map : pool) {
            if (map.id().equalsIgnoreCase(id.trim())) {
                return Optional.of(map);
            }
        }
        return Optional.empty();
    }

    /** 票数最高者胜；平票时在并列的几张里按权重随机。 */
    private static Optional<MapDefinition> byVote(List<MapDefinition> pool,
                                                 Map<String, Integer> votes) {
        List<MapDefinition> voted = new ArrayList<>();
        int best = 0;
        for (MapDefinition map : pool) {
            Integer count = votes.get(map.id());
            if (count == null || count <= 0) {
                continue;
            }
            if (count > best) {
                best = count;
                voted.clear();
            }
            if (count == best) {
                voted.add(map);
            }
        }
        if (voted.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(weightedRandom(voted));
    }

    private MapDefinition byRotation(List<MapDefinition> pool) {
        int index = Math.floorMod(rotateIndex, pool.size());
        rotateIndex = index + 1;
        return pool.get(index);
    }

    /** 避开最近用过的图；最近用过的占满候选时，至少避开上一张。 */
    private MapDefinition avoidRepeat(List<MapDefinition> pool) {
        if (!config.avoidRepeat() || pool.size() <= 1) {
            return weightedRandom(pool);
        }

        List<MapDefinition> fresh = new ArrayList<>(pool.size());
        for (MapDefinition map : pool) {
            if (!recent.contains(map.id())) {
                fresh.add(map);
            }
        }
        if (!fresh.isEmpty()) {
            return weightedRandom(fresh);
        }

        String last = recent.peekLast();
        List<MapDefinition> notLast = new ArrayList<>(pool.size());
        for (MapDefinition map : pool) {
            if (!map.id().equals(last)) {
                notLast.add(map);
            }
        }
        return weightedRandom(notLast.isEmpty() ? pool : notLast);
    }

    private static MapDefinition weightedRandom(List<MapDefinition> pool) {
        if (pool.size() == 1) {
            return pool.get(0);
        }
        double total = 0;
        for (MapDefinition map : pool) {
            total += map.weight();
        }
        if (total <= 0) {
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double accumulated = 0;
        for (MapDefinition map : pool) {
            accumulated += map.weight();
            if (roll < accumulated) {
                return map;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private Pick accept(MapDefinition map, String source) {
        recent.addLast(map.id());
        int limit = Math.max(1, config.avoidRepeatHistory());
        while (recent.size() > limit) {
            recent.removeFirst();
        }
        return new Pick(map, source);
    }
}
