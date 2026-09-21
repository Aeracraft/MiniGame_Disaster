package com.xcreate.disaster.disaster;

import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.map.MapBounds;
import com.xcreate.disaster.map.MapPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * 按灾种的落点策略取点，并让每个点过一遍校验链。
 *
 * <p>每个落点独立重抽，最多试 {@code max-retries} 次。凑不满就少落几个——部分落点的流星雨
 * 也好过把流星砸到图外，或者把整场灾难取消掉。原因会记进 {@link SpawnOutcome}，
 * 服主回头能看出是地图太小还是地面已经打没了。</p>
 *
 * <p>地图定义里若是给这个灾种标了固定落点，就用那些点，不再随机。</p>
 */
public final class SpawnPlanner {

    /** 取不到半径参数时的默认散落半径（格）。 */
    private static final int DEFAULT_RADIUS = 8;

    /** 取不到贴边宽度时的默认值（格）。 */
    private static final int DEFAULT_EDGE_WIDTH = 8;

    /** 取不到采样数时的默认值（列）。 */
    private static final int DEFAULT_SAMPLES = 24;

    /** 围着某个中心取点时，为了不越界最多重掷几次。 */
    private static final int AROUND_DRAWS = 8;

    private PluginConfig config;
    private final Random random;
    private final SpawnValidator validator;

    public SpawnPlanner(PluginConfig config, Random random) {
        this.config = config;
        this.random = random;
        this.validator = new SpawnValidator(config.disasters());
    }

    public void apply(PluginConfig config) {
        this.config = config;
        this.validator.apply(config.disasters());
    }

    /**
     * 规划一次落点。
     *
     * @param terrain 地形读取入口。真实实现读世界，测试实现读一张表
     */
    public SpawnOutcome plan(DisasterDefinition definition, SpawnContext context,
                             SpawnTerrain terrain) {
        if (!definition.producesPoints()) {
            return SpawnOutcome.none();
        }
        if (!context.hasBounds()) {
            return SpawnOutcome.rejected(definition.pointCount(), SpawnRejection.NO_BOUNDS);
        }

        List<MapPoint> anchors = context.anchorsOf(definition.id());
        if (!anchors.isEmpty()) {
            return placeAnchors(anchors, context, terrain);
        }

        SpawnStrategy strategy = definition.strategy();
        if (strategy.needsPlayers() && context.players().isEmpty()) {
            return SpawnOutcome.rejected(requestedCount(definition, context),
                    SpawnRejection.NO_PLAYERS);
        }

        int requested = requestedCount(definition, context);
        int retries = Math.max(1, config.disasters().maxRetries());
        List<MapPoint> accepted = new ArrayList<>(requested);
        Map<SpawnRejection, Integer> rejections = new EnumMap<>(SpawnRejection.class);
        int attempts = 0;

        for (int index = 0; index < requested; index++) {
            for (int attempt = 0; attempt < retries; attempt++) {
                int[] spot = draw(strategy, definition, context, index, terrain, accepted);
                attempts++;
                if (spot == null) {
                    continue;
                }
                Optional<SpawnRejection> rejection =
                        validator.check(context, terrain, spot[0], spot[1], accepted, true);
                if (rejection.isPresent()) {
                    rejections.merge(rejection.get(), 1, Integer::sum);
                    continue;
                }
                accepted.add(toPoint(context, terrain, spot[0], spot[1]));
                break;
            }
        }

        return new SpawnOutcome(requested, accepted, attempts, rejections);
    }

    /** AROUND_EACH_PLAYER 的落点数是「每人几个」，其余按总数列。 */
    private static int requestedCount(DisasterDefinition definition, SpawnContext context) {
        if (definition.strategy() == SpawnStrategy.AROUND_EACH_PLAYER) {
            return definition.pointCount() * context.players().size();
        }
        return definition.pointCount();
    }

    /** 服主手标的固定落点照单全收，只查边界与地面，不查间距。 */
    private SpawnOutcome placeAnchors(List<MapPoint> anchors, SpawnContext context,
                                      SpawnTerrain terrain) {
        List<MapPoint> accepted = new ArrayList<>(anchors.size());
        Map<SpawnRejection, Integer> rejections = new EnumMap<>(SpawnRejection.class);
        for (MapPoint anchor : anchors) {
            Optional<SpawnRejection> rejection = validator.check(context, terrain,
                    anchor.blockX(), anchor.blockZ(), accepted, false);
            if (rejection.isPresent()) {
                rejections.merge(rejection.get(), 1, Integer::sum);
                continue;
            }
            accepted.add(anchor.inWorld(context.worldName()));
        }
        return new SpawnOutcome(anchors.size(), accepted, anchors.size(), rejections);
    }

    private int[] draw(SpawnStrategy strategy, DisasterDefinition definition,
                       SpawnContext context, int index, SpawnTerrain terrain,
                       List<MapPoint> accepted) {
        MapBounds bounds = context.bounds();
        return switch (strategy) {
            case NONE -> null;
            case RANDOM_IN_BOUNDS -> new int[]{
                    randomIn(bounds.minX(), bounds.maxX()),
                    randomIn(bounds.minZ(), bounds.maxZ())};
            case NEAR_PLAYER -> {
                MapPoint player = context.players().get(random.nextInt(context.players().size()));
                yield around(player.x(), player.z(), radius(definition), bounds);
            }
            case AROUND_EACH_PLAYER -> {
                int perPlayer = Math.max(1, definition.pointCount());
                MapPoint player = context.players()
                        .get(Math.floorMod(index / perPlayer, context.players().size()));
                yield around(player.x(), player.z(), radius(definition), bounds);
            }
            case HIGH_POINTS -> highPoint(definition, bounds, terrain, accepted);
            case FROM_EDGE -> edgePoint(definition, bounds);
            case AT_MAP_CENTER -> around(
                    (bounds.minX() + bounds.maxX()) / 2.0,
                    (bounds.minZ() + bounds.maxZ()) / 2.0,
                    radius(definition), bounds);
        };
    }

    private static MapPoint toPoint(SpawnContext context, SpawnTerrain terrain, int x, int z) {
        int groundY = terrain.groundY(x, z);
        return new MapPoint(context.worldName(), "", x + 0.5, groundY + 1, z + 0.5, 0f, 0f);
    }

    /** 在一个中心周围随机散开。快贴边时先重掷几次，尽量别把落点推到图外。 */
    private int[] around(double centerX, double centerZ, int radius, MapBounds bounds) {
        int baseX = (int) Math.floor(centerX);
        int baseZ = (int) Math.floor(centerZ);
        int x = baseX;
        int z = baseZ;
        for (int attempt = 0; attempt < AROUND_DRAWS; attempt++) {
            x = baseX + offset(radius);
            z = baseZ + offset(radius);
            if (inside(x, z, bounds)) {
                return new int[]{x, z};
            }
        }
        return new int[]{clamp(x, bounds.minX(), bounds.maxX()),
                clamp(z, bounds.minZ(), bounds.maxZ())};
    }

    /**
     * 高点：随机采样若干列，取其中最高的。
     *
     * <p>不做全图普查——那是一次几万次列读取，多开几局就能把主线程拖跨。采样必然不精确，
     * 但对「往高处劈雷」来说够用，而且代价是常数级的。</p>
     */
    private int[] highPoint(DisasterDefinition definition, MapBounds bounds,
                            SpawnTerrain terrain, List<MapPoint> accepted) {
        int samples = clamp(definition.options().getInt("samples", DEFAULT_SAMPLES), 8, 512);
        List<int[]> sampled = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            int x = randomIn(bounds.minX(), bounds.maxX());
            int z = randomIn(bounds.minZ(), bounds.maxZ());
            int y = terrain.groundY(x, z);
            if (y != SpawnTerrain.NO_GROUND) {
                sampled.add(new int[]{x, z, y});
            }
        }
        if (sampled.isEmpty()) {
            return null;
        }
        sampled.sort(Comparator.comparingInt((int[] item) -> item[2]).reversed());
        for (int[] candidate : sampled) {
            if (!columnTaken(accepted, candidate[0], candidate[1])) {
                return new int[]{candidate[0], candidate[1]};
            }
        }
        return new int[]{sampled.get(0)[0], sampled.get(0)[1]};
    }

    /** 贴着某一条边取点，用来做「从图外涌入」的灾难。 */
    private int[] edgePoint(DisasterDefinition definition, MapBounds bounds) {
        int width = clamp(definition.options().getInt("edge-width", DEFAULT_EDGE_WIDTH), 0, 256);
        int westHigh = Math.min(bounds.maxX(), bounds.minX() + width);
        int northHigh = Math.min(bounds.maxZ(), bounds.minZ() + width);
        int eastLow = Math.max(bounds.minX(), bounds.maxX() - width);
        int southLow = Math.max(bounds.minZ(), bounds.maxZ() - width);

        return switch (random.nextInt(4)) {
            case 0 -> new int[]{randomIn(bounds.minX(), westHigh),
                    randomIn(bounds.minZ(), bounds.maxZ())};
            case 1 -> new int[]{randomIn(eastLow, bounds.maxX()),
                    randomIn(bounds.minZ(), bounds.maxZ())};
            case 2 -> new int[]{randomIn(bounds.minX(), bounds.maxX()),
                    randomIn(bounds.minZ(), northHigh)};
            default -> new int[]{randomIn(bounds.minX(), bounds.maxX()),
                    randomIn(southLow, bounds.maxZ())};
        };
    }

    private int offset(int radius) {
        return radius <= 0 ? 0 : random.nextInt(radius * 2 + 1) - radius;
    }

    private int randomIn(int min, int max) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    private int radius(DisasterDefinition definition) {
        return clamp(definition.options().getInt("radius", DEFAULT_RADIUS), 0, 256);
    }

    private static boolean inside(int x, int z, MapBounds bounds) {
        return x >= bounds.minX() && x <= bounds.maxX()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }

    private static boolean columnTaken(List<MapPoint> accepted, int x, int z) {
        for (MapPoint point : accepted) {
            if (point.blockX() == x && point.blockZ() == z) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
