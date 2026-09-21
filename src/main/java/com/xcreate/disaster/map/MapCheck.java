package com.xcreate.disaster.map;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 地图定义的校验规则。
 *
 * <p>规则只有一份，{@link MapDefinition#playable()} 与 {@code /ds map info} 都从这里取结论，
 * 免得出现「命令说没问题、开局却开不起来」这种对不上的情况。</p>
 */
public final class MapCheck {

    /**
     * 地图 id 的合法形式。
     *
     * <p>id 会拼进副本世界名（{@code ds_<id>_<序号>}），世界名只收小写字母、数字、
     * 下划线、点和连字符，所以中文只能放在 displayName 里。</p>
     */
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9_.-]{0,46}[a-z0-9])?$");

    private static final int MIN_SIDE = 16;
    private static final int MAX_SIDE = 2048;
    private static final int MIN_HEIGHT = 8;

    private MapCheck() {
    }

    public static List<MapIssue> check(MapDefinition definition) {
        List<MapIssue> issues = new ArrayList<>();

        checkId(definition, issues);
        checkWorld(definition, issues);
        checkBounds(definition, issues);
        checkSpawns(definition, issues);
        checkPlayers(definition, issues);
        checkAnchors(definition, issues);

        if (!definition.enabled()) {
            issues.add(MapIssue.warning("地图当前处于停用状态，不会被抽到。"));
        }
        return issues;
    }

    /** 只看会让地图无法开局的项。 */
    public static List<MapIssue> errors(MapDefinition definition) {
        List<MapIssue> issues = new ArrayList<>();
        checkId(definition, issues);
        checkWorld(definition, issues);
        checkBounds(definition, issues);
        checkSpawns(definition, issues);
        checkPlayers(definition, issues);
        return issues;
    }

    public static boolean validId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    private static void checkId(MapDefinition definition, List<MapIssue> issues) {
        String id = definition.id();
        if (id == null || id.isBlank()) {
            issues.add(MapIssue.error("地图 id 为空。"));
        } else if (!validId(id)) {
            issues.add(MapIssue.error("地图 id 只能用小写字母、数字、下划线、点和连字符，"
                    + "且首尾必须是字母或数字（当前：" + id + "）。中文请填 display-name。"));
        }
    }

    private static void checkWorld(MapDefinition definition, List<MapIssue> issues) {
        if (definition.world().isBlank()) {
            issues.add(MapIssue.error("尚未记录模板世界名，请站在图里执行 /ds map bounds <id>。"));
        }
    }

    private static void checkBounds(MapDefinition definition, List<MapIssue> issues) {
        MapBounds bounds = definition.bounds();
        if (bounds == null) {
            issues.add(MapIssue.error("尚未设置边界（bounds），无法开局。"));
            return;
        }
        if (bounds.sizeX() < MIN_SIDE || bounds.sizeZ() < MIN_SIDE) {
            issues.add(MapIssue.warning("地图水平尺寸只有 " + bounds.sizeX() + "×" + bounds.sizeZ()
                    + "，灾难几乎没有落点空间。"));
        }
        if (bounds.sizeX() > MAX_SIDE || bounds.sizeZ() > MAX_SIDE) {
            issues.add(MapIssue.warning("地图水平尺寸达到 " + bounds.sizeX() + "×" + bounds.sizeZ()
                    + "，随机落点与出界判定都会变慢。"));
        }
        if (bounds.sizeY() < MIN_HEIGHT) {
            issues.add(MapIssue.warning("地图高度只有 " + bounds.sizeY() + " 格。"));
        }
    }

    private static void checkSpawns(MapDefinition definition, List<MapIssue> issues) {
        List<MapPoint> spawns = definition.spawns();
        if (spawns.size() < MapDefinition.MIN_SPAWNS) {
            issues.add(MapIssue.error("出生点不足，至少需要 " + MapDefinition.MIN_SPAWNS
                    + " 个（当前 " + spawns.size() + " 个）。"));
        }
        for (MapPoint spawn : spawns) {
            if (!spawn.world().isBlank() && !spawn.world().equals(definition.world())) {
                issues.add(MapIssue.error("出生点 " + spawn.shortText()
                        + " 来自其他世界（" + spawn.world() + "）。"));
                break;
            }
        }
        MapBounds bounds = definition.bounds();
        if (bounds != null) {
            long outside = spawns.stream().filter(spawn -> !bounds.contains(spawn)).count();
            if (outside > 0) {
                issues.add(MapIssue.warning("有 " + outside + " 个出生点落在边界外，"
                        + "开局时会被弹回地面。"));
            }
        }
        if (definition.spectatorSpawn() == null) {
            issues.add(MapIssue.warning("尚未设置观战点，出局的玩家将停在原地。"));
        }
    }

    private static void checkPlayers(MapDefinition definition, List<MapIssue> issues) {
        int min = definition.minPlayers();
        int max = definition.maxPlayers();
        if (min != MapDefinition.FOLLOW_GLOBAL && max != MapDefinition.FOLLOW_GLOBAL && min > max) {
            issues.add(MapIssue.error("min-players(" + min + ") 大于 max-players(" + max + ")。"));
        }
    }

    private static void checkAnchors(MapDefinition definition, List<MapIssue> issues) {
        MapBounds bounds = definition.bounds();
        if (bounds == null) {
            return;
        }
        for (var entry : definition.anchors().entrySet()) {
            long outside = entry.getValue().stream().filter(point -> !bounds.contains(point)).count();
            if (outside > 0) {
                issues.add(MapIssue.warning("灾难 " + entry.getKey() + " 有 " + outside
                        + " 个固定落点在边界外。"));
            }
        }
    }
}
