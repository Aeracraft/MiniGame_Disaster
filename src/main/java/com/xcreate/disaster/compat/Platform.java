package com.xcreate.disaster.compat;

import org.bukkit.Bukkit;

/**
 * 运行平台探测。目标是「Spigot 打底 + 探测到 Paper 时启用增强」。
 *
 * <p>关键约束：Paper 专属代码必须放在<b>独立包</b>里，并且只在
 * {@link #isPaper()} 为真时才加载。否则在 Spigot 上会因为类缺失直接抛
 * {@code NoClassDefFoundError}，而且这个错误发生在类加载阶段，
 * try/catch 是拦不住的。</p>
 */
public final class Platform {

    /** 存在任意一个即判定为 Paper 系服务端。 */
    private static final String[] PAPER_MARKER_CLASSES = {
            "io.papermc.paper.configuration.GlobalConfiguration",
            "io.papermc.paper.plugin.provider.classloader.PaperClassLoader",
            "com.destroystokyo.paper.utils.PaperPluginLogger",
            "com.destroystokyo.paper.PaperConfig"
    };

    /** 存在任意一个即判定为 Folia（本插件不支持 Folia 的调度模型）。 */
    private static final String[] FOLIA_MARKER_CLASSES = {
            "io.papermc.paper.threadedregions.RegionizedServer",
            "io.papermc.paper.threadedregions.ThreadedRegionizer"
    };

    private final String brand;
    private final boolean paper;
    private final boolean folia;

    private Platform(String brand, boolean paper, boolean folia) {
        this.brand = brand;
        this.paper = paper;
        this.folia = folia;
    }

    public static Platform detect() {
        String brand = safeVersionString();
        String lower = brand.toLowerCase(java.util.Locale.ROOT);

        boolean folia = hasAnyClass(FOLIA_MARKER_CLASSES) || lower.contains("folia");
        boolean paper = hasAnyClass(PAPER_MARKER_CLASSES)
                || lower.contains("paper")
                || lower.contains("purpur")
                || lower.contains("pufferfish");

        return new Platform(brand, paper, folia);
    }

    private static String safeVersionString() {
        try {
            String v = Bukkit.getVersion();
            return v == null ? "" : v;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean hasAnyClass(String[] names) {
        for (String name : names) {
            if (Reflect.hasClass(name)) {
                return true;
            }
        }
        return false;
    }

    /** 服务端品牌字符串，例如 {@code git-Paper-123 (MC: 1.20.4)}。 */
    public String brand() {
        return brand;
    }

    public boolean isPaper() {
        return paper;
    }

    public boolean isFolia() {
        return folia;
    }

    /** 是否应启用 Adventure / MiniMessage 等 Paper 专属增强。 */
    public boolean supportsAdventure() {
        return paper && Reflect.hasClass("net.kyori.adventure.text.Component");
    }

    @Override
    public String toString() {
        return brand + " [paper=" + paper + ", folia=" + folia + "]";
    }
}
