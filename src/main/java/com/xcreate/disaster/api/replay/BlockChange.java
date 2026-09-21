package com.xcreate.disaster.api.replay;

/**
 * 一次方块变更。
 *
 * <p>材质用字符串而不是 {@code org.bukkit.Material}，原因有两个：
 * 一是跨版本枚举增删频繁，字符串不会因为某个常量消失而抛异常；
 * 二是它需要能安全地跨进程（写进录像文件、发进 webhook）传递。</p>
 *
 * @param world 世界名
 * @param x     方块坐标 X
 * @param y     方块坐标 Y
 * @param z     方块坐标 Z
 * @param from  变更前的材质名，例如 {@code STONE}
 * @param to    变更后的材质名，例如 {@code AIR}
 */
public record BlockChange(
        String world,
        int x,
        int y,
        int z,
        String from,
        String to
) {

    public boolean isRemoval() {
        return "AIR".equals(to);
    }
}
