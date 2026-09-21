package com.xcreate.disaster.room;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 副本世界的命名。
 *
 * <p>格式固定为 {@code ds_<地图 id>_<序号>}。地图 id 只允许英文小写字母、数字、下划线、
 * 点和连字符，所以拼出来的名字天然满足服务端对世界名的校验。</p>
 *
 * <p>用世界名同时充当房间标识：Bukkit 里同一个世界名同一时刻只能加载一次，
 * 拿它当键既唯一又省一张映射表。</p>
 */
public final class RoomNaming {

    /** 副本世界的前缀，用来把本插件生成的世界跟服主自己的世界区分开。 */
    public static final String PREFIX = "ds_";

    /** 同样的地图最多并行开这么多份，够用且能挡住序号失控。 */
    private static final int MAX_INDEX = 999;

    private RoomNaming() {
    }

    public static String worldName(String mapId, int index) {
        return PREFIX + mapId + "_" + index;
    }

    /**
     * 挑一个没被占用的世界名。
     *
     * @param taken 已被占用的世界名，大小写不敏感
     * @return 全都占满时返回 {@code null}，由调用方决定是拒绝还是排队
     */
    public static String nextWorldName(String mapId, Set<String> taken) {
        Set<String> lowered = new HashSet<>();
        if (taken != null) {
            for (String name : taken) {
                if (name != null) {
                    lowered.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        for (int index = 1; index <= MAX_INDEX; index++) {
            String candidate = worldName(mapId, index);
            if (!lowered.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    /** 判断一个世界名是不是本插件生成的副本。 */
    public static boolean isRoomWorld(String worldName) {
        return worldName != null && worldName.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    /**
     * 从世界名反解地图 id：{@code ds_my_city_2} 得到 {@code my_city}。
     *
     * <p>地图 id 本身可以带下划线，所以从右边找最后一个下划线，且后面必须是纯数字才算数。</p>
     *
     * @return 不是本插件命名的世界时返回 {@code null}
     */
    public static String mapIdOf(String worldName) {
        if (!isRoomWorld(worldName)) {
            return null;
        }
        String body = worldName.substring(PREFIX.length());
        int split = body.lastIndexOf('_');
        if (split <= 0) {
            return null;
        }
        String tail = body.substring(split + 1);
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) {
                return null;
            }
        }
        return body.substring(0, split);
    }
}
