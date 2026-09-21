package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapBounds;
import com.xcreate.disaster.map.MapDefinition;
import com.xcreate.disaster.map.MapPoint;

import java.util.List;
import java.util.Map;

/**
 * 取落点时能看到的世界切片。
 *
 * <p>坐标一律用 {@link MapPoint} 而不是 {@code Location}，所以这一层不依赖 Bukkit 世界对象，
 * 整个落点规划可以脱离服务端跑测试。要真正落地方块时，调用方再用副本世界名把坐标还原成
 * {@code Location}。</p>
 *
 * @param bounds    地图边界，随机落点必须先被它裁掉，否则会砸到图外甚至隔壁世界
 * @param players   场上还活着的玩家坐标，围着玩家取点的灾种要用
 * @param mapSpawns 地图出生点，用来避免灾难直接砸在开局点上
 * @param anchors   服主为具体灾种标好的固定落点，某个灾种没标就是纯随机
 */
public record SpawnContext(String worldName, MapBounds bounds, List<MapPoint> players,
                           List<MapPoint> mapSpawns, Map<String, List<MapPoint>> anchors) {

    public SpawnContext {
        worldName = worldName == null ? "" : worldName;
        players = players == null ? List.of() : List.copyOf(players);
        mapSpawns = mapSpawns == null ? List.of() : List.copyOf(mapSpawns);
        anchors = anchors == null ? Map.of() : Map.copyOf(anchors);
    }

    /** 由地图定义与玩家坐标拼出一份，副本世界的坐标换算交给调用方。 */
    public static SpawnContext of(MapDefinition map, List<MapPoint> players) {
        return new SpawnContext(map.world(), map.bounds(), players,
                map.spawns(), map.anchors());
    }

    public boolean hasBounds() {
        return bounds != null;
    }

    public List<MapPoint> anchorsOf(String disasterId) {
        return anchors.getOrDefault(disasterId, List.of());
    }
}
