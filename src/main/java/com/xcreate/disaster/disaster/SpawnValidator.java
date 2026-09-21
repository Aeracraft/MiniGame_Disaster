package com.xcreate.disaster.disaster;

import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.map.MapBounds;
import com.xcreate.disaster.map.MapPoint;

import java.util.List;
import java.util.Optional;

/**
 * 落点校验链。
 *
 * <p>随机落点在被采纳前必须逐条过这里。灾难是砸向地图的破坏源，一个没校验的坐标轻则砸进
 * 虚空里的建筑残骸，重则落在边界外的隔壁世界——所以「宁可这一波少落几个，也不乱砸」。</p>
 *
 * <p>顺序按代价从低到高：先看坐标在不在框里，再读地形，最后才做两次距离比较。</p>
 */
public final class SpawnValidator {

    private PluginConfig.Disasters config;

    public SpawnValidator(PluginConfig.Disasters config) {
        this.config = config;
    }

    public void apply(PluginConfig.Disasters config) {
        this.config = config;
    }

    /**
     * 校验一个候选落点。
     *
     * @param blockX   候选点的方块 X
     * @param blockZ   候选点的方块 Z
     * @param accepted 本次已经确定下来的落点，用来判断间距
     * @param enforceSpacing 是否要求与已确定的落点保持间距。服主手标的固定落点不查这一条——
     *                       那些点是他自己摆的，扎堆多半是有意为之
     * @return 通过返回空，否则是被拒的原因
     */
    public Optional<SpawnRejection> check(SpawnContext context, SpawnTerrain terrain,
                                          int blockX, int blockZ,
                                          List<MapPoint> accepted, boolean enforceSpacing) {
        MapBounds bounds = context.bounds();
        if (bounds == null) {
            return Optional.of(SpawnRejection.NO_BOUNDS);
        }
        if (blockX < bounds.minX() || blockX > bounds.maxX()
                || blockZ < bounds.minZ() || blockZ > bounds.maxZ()) {
            return Optional.of(SpawnRejection.OUT_OF_BOUNDS);
        }

        int groundY = terrain.groundY(blockX, blockZ);
        if (groundY == SpawnTerrain.NO_GROUND) {
            return Optional.of(SpawnRejection.NO_GROUND);
        }
        if (groundY < bounds.minY() || groundY > bounds.maxY()) {
            return Optional.of(SpawnRejection.OUT_OF_BOUNDS);
        }

        double centerX = blockX + 0.5;
        double centerZ = blockZ + 0.5;

        int spawnGap = config.minDistanceFromSpawn();
        if (spawnGap > 0) {
            for (MapPoint spawn : context.mapSpawns()) {
                if (spawn.horizontalDistanceTo(centerX, centerZ) < spawnGap) {
                    return Optional.of(SpawnRejection.TOO_CLOSE_TO_SPAWN);
                }
            }
        }

        int pointGap = config.minDistanceBetweenPoints();
        if (enforceSpacing && pointGap > 0) {
            for (MapPoint existing : accepted) {
                if (existing.horizontalDistanceTo(centerX, centerZ) < pointGap) {
                    return Optional.of(SpawnRejection.TOO_CLOSE_TO_POINT);
                }
            }
        }

        return Optional.empty();
    }
}
