package com.xcreate.disaster.disaster;

import java.util.List;
import java.util.Locale;

/**
 * 随插件一起发布的内置灾种。
 *
 * <p>一期 8 个主灾 + 2 个次灾，对齐 Hypixel 的玩法但大幅裁剪了数量。定义本身放在
 * {@code resources/disasters/} 下，首次运行复制到数据目录，之后服主直接改副本文件。</p>
 *
 * <p>这里只留 id 清单，用来决定要释放哪些文件。新增灾种 = 加一个资源文件 + 这里加一个 id，
 * 运行时行为另在代码里按 id 挂上。</p>
 */
public final class BuiltinDisasters {

    /** 内置文件的相对路径前缀，释放时用作 resource 名。 */
    private static final String RESOURCE_DIR = "disasters/";

    /** 字段说明文件，下划线开头所以不会被当定义加载。 */
    public static final String EXAMPLE_FILE = "_example.yml";

    public static final List<String> IDS = List.of(
            "meteor_shower",
            "lightning",
            "sinkhole",
            "tornado",
            "flood",
            "acid_rain",
            "zombie_apocalypse",
            "floor_is_lava",
            "anvil_rain",
            "purge");

    private BuiltinDisasters() {
    }

    public static String resourceOf(String id) {
        return RESOURCE_DIR + id + ".yml";
    }

    public static boolean isBuiltin(String id) {
        return id != null && IDS.contains(id.toLowerCase(Locale.ROOT));
    }
}
