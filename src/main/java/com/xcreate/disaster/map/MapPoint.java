package com.xcreate.disaster.map;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * 地图上的一个点位。
 *
 * <p>坐标与角度写进 YAML 前会保留两位小数，手改文件时不会看到一长串浮点尾巴，
 * 而 0.01 格的误差在玩法上没有意义。</p>
 *
 * <p>世界名不写进点位的 YAML，它由地图定义统一给出。创建副本时整组点位会被
 * 重定向到副本世界，见 {@link #inWorld(String)}。</p>
 *
 * @param world 模板世界名，重定向后为副本世界名
 * @param name  给服主看的标签，可以为空
 */
public record MapPoint(String world, String name, double x, double y, double z,
                       float yaw, float pitch) {

    public MapPoint {
        world = world == null ? "" : world;
        name = name == null ? "" : name;
        x = round(x);
        y = round(y);
        z = round(z);
        yaw = round(yaw);
        pitch = round(pitch);
    }

    public static MapPoint of(Location location) {
        return of(location, "");
    }

    public static MapPoint of(Location location, String name) {
        World world = location.getWorld();
        return new MapPoint(world == null ? "" : world.getName(), name,
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public MapPoint inWorld(String world) {
        return new MapPoint(world, name, x, y, z, yaw, pitch);
    }

    public MapPoint withName(String name) {
        return new MapPoint(world, name, x, y, z, yaw, pitch);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    public double distanceTo(double otherX, double otherY, double otherZ) {
        double dx = x - otherX;
        double dy = y - otherY;
        double dz = z - otherZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** 水平距离，忽略高度差。校验落点间距时用这个，免得屋顶上的点被误判成很远。 */
    public double horizontalDistanceTo(double otherX, double otherZ) {
        double dx = x - otherX;
        double dz = z - otherZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public String shortText() {
        return String.format("%.2f, %.2f, %.2f", x, y, z);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static float round(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }
}
