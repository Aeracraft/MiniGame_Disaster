package com.xcreate.disaster.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一张地图的完整定义。
 *
 * <p>不可变。要改点位走 {@link #toBuilder()}，改完交给 {@link MapRegistry} 落盘。</p>
 *
 * <p>{@code world} 是作图时用的模板世界名，定义里所有坐标都属于它。开局时用
 * {@link #inWorld(String)} 整组换到副本世界，不必逐个改写点位。</p>
 *
 * @param weight     随机抽图时的相对权重
 * @param minPlayers 少于全局默认时填 {@link #FOLLOW_GLOBAL}
 * @param anchors    灾难 id 到固定落点的映射；某场灾难不配则纯随机
 */
public record MapDefinition(
        String id,
        String displayName,
        String templateFolder,
        String world,
        boolean enabled,
        double weight,
        int minPlayers,
        int maxPlayers,
        MapBounds bounds,
        List<MapPoint> spawns,
        MapPoint spectatorSpawn,
        Map<String, List<MapPoint>> anchors,
        List<MapPoint> lootChests) {

    /** 玩家数跟随全局配置。 */
    public static final int FOLLOW_GLOBAL = -1;

    /** 玩家出生点的下限，少于这个数就没法散开开局。 */
    public static final int MIN_SPAWNS = 2;

    public MapDefinition {
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        templateFolder = templateFolder == null || templateFolder.isBlank() ? id : templateFolder;
        world = world == null ? "" : world;
        weight = weight > 0 ? weight : 1.0;
        spawns = spawns == null ? List.of() : List.copyOf(spawns);
        lootChests = lootChests == null ? List.of() : List.copyOf(lootChests);
        anchors = copyAnchors(anchors);
    }

    /** 空定义，用于 {@code /ds map create}。 */
    public static MapDefinition blank(String id, String displayName) {
        return new MapDefinition(id, displayName, id, "", true, 1.0,
                FOLLOW_GLOBAL, FOLLOW_GLOBAL, null,
                List.of(), null, Map.of(), List.of());
    }

    /** 能否真正开局：开关打开，且校验里没有任何 ERROR。 */
    public boolean playable() {
        return enabled && MapCheck.errors(this).isEmpty();
    }

    /** 换到副本世界，返回一份新的定义，原对象不动。 */
    public MapDefinition inWorld(String runtimeWorld) {
        List<MapPoint> movedSpawns = new ArrayList<>(spawns.size());
        for (MapPoint spawn : spawns) {
            movedSpawns.add(spawn.inWorld(runtimeWorld));
        }
        List<MapPoint> movedLoot = new ArrayList<>(lootChests.size());
        for (MapPoint chest : lootChests) {
            movedLoot.add(chest.inWorld(runtimeWorld));
        }
        Map<String, List<MapPoint>> movedAnchors = new LinkedHashMap<>();
        for (Map.Entry<String, List<MapPoint>> entry : anchors.entrySet()) {
            List<MapPoint> points = new ArrayList<>(entry.getValue().size());
            for (MapPoint point : entry.getValue()) {
                points.add(point.inWorld(runtimeWorld));
            }
            movedAnchors.put(entry.getKey(), points);
        }
        return new MapDefinition(id, displayName, templateFolder, runtimeWorld,
                enabled, weight, minPlayers, maxPlayers, bounds,
                movedSpawns, spectatorSpawn == null ? null : spectatorSpawn.inWorld(runtimeWorld),
                movedAnchors, movedLoot);
    }

    public List<MapPoint> anchorsOf(String disasterId) {
        return anchors.getOrDefault(disasterId, List.of());
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    private static Map<String, List<MapPoint>> copyAnchors(Map<String, List<MapPoint>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<MapPoint>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<MapPoint>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    /** 可变副本，只在命令处理与生成流程里短暂存在。 */
    public static final class Builder {

        private String id;
        private String displayName;
        private String templateFolder;
        private String world;
        private boolean enabled;
        private double weight;
        private int minPlayers;
        private int maxPlayers;
        private MapBounds bounds;
        private final List<MapPoint> spawns = new ArrayList<>();
        private MapPoint spectatorSpawn;
        private final Map<String, List<MapPoint>> anchors = new LinkedHashMap<>();
        private final List<MapPoint> lootChests = new ArrayList<>();

        private Builder(MapDefinition source) {
            this.id = source.id();
            this.displayName = source.displayName();
            this.templateFolder = source.templateFolder();
            this.world = source.world();
            this.enabled = source.enabled();
            this.weight = source.weight();
            this.minPlayers = source.minPlayers();
            this.maxPlayers = source.maxPlayers();
            this.bounds = source.bounds();
            this.spawns.addAll(source.spawns());
            this.spectatorSpawn = source.spectatorSpawn();
            source.anchors().forEach((key, value) -> this.anchors.put(key, new ArrayList<>(value)));
            this.lootChests.addAll(source.lootChests());
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder templateFolder(String templateFolder) {
            this.templateFolder = templateFolder;
            return this;
        }

        public Builder world(String world) {
            this.world = world;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder weight(double weight) {
            this.weight = weight;
            return this;
        }

        public Builder bounds(MapBounds bounds) {
            this.bounds = bounds;
            return this;
        }

        public Builder spectatorSpawn(MapPoint point) {
            this.spectatorSpawn = point;
            return this;
        }

        public Builder addSpawn(MapPoint point) {
            this.spawns.add(point);
            return this;
        }

        public Builder clearSpawns() {
            this.spawns.clear();
            return this;
        }

        /** 序号从 1 开始，和 {@code /ds map spawn list} 里显示的一致。 */
        public boolean removeSpawn(int index) {
            if (index < 1 || index > spawns.size()) {
                return false;
            }
            spawns.remove(index - 1);
            return true;
        }

        public Builder addAnchor(String disasterId, MapPoint point) {
            anchors.computeIfAbsent(disasterId, key -> new ArrayList<>()).add(point);
            return this;
        }

        public Builder clearAnchors(String disasterId) {
            anchors.remove(disasterId);
            return this;
        }

        public Builder addLootChest(MapPoint point) {
            this.lootChests.add(point);
            return this;
        }

        public Builder clearLootChests() {
            this.lootChests.clear();
            return this;
        }

        public MapDefinition build() {
            Map<String, List<MapPoint>> frozen = new LinkedHashMap<>();
            anchors.forEach((key, value) -> {
                if (!value.isEmpty()) {
                    frozen.put(key, value);
                }
            });
            return new MapDefinition(id, displayName, templateFolder, world, enabled, weight,
                    minPlayers, maxPlayers, bounds, spawns, spectatorSpawn, frozen, lootChests);
        }
    }
}
