package com.xcreate.disaster.map;

import org.bukkit.Location;

/**
 * 地图的可用范围，闭区间，单位是方块。
 *
 * <p>灾难随机落点、出界判定都以它为准，所以取整时下界向下取、上界向上取，
 * 宁可多框一格也不要漏掉边缘。</p>
 */
public record MapBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** 由两个对角点构造，自动排序两端的先后。 */
    public static MapBounds between(Location a, Location b) {
        return new MapBounds(
                (int) Math.floor(Math.min(a.getX(), b.getX())),
                (int) Math.floor(Math.min(a.getY(), b.getY())),
                (int) Math.floor(Math.min(a.getZ(), b.getZ())),
                (int) Math.floor(Math.max(a.getX(), b.getX())),
                (int) Math.floor(Math.max(a.getY(), b.getY())),
                (int) Math.floor(Math.max(a.getZ(), b.getZ())));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1
                && y >= minY && y < maxY + 1
                && z >= minZ && z < maxZ + 1;
    }

    public boolean contains(MapPoint point) {
        return contains(point.x(), point.y(), point.z());
    }

    public MapBounds expand(int blocks) {
        return new MapBounds(minX - blocks, minY - blocks, minZ - blocks,
                maxX + blocks, maxY + blocks, maxZ + blocks);
    }

    public String shortText() {
        return minX + ".." + maxX + ", " + minY + ".." + maxY + ", " + minZ + ".." + maxZ;
    }
}
