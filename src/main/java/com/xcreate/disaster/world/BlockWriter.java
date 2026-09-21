package com.xcreate.disaster.world;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.api.event.DisasterBlockChangedEvent;
import com.xcreate.disaster.api.replay.BlockChange;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;

/**
 * 所有方块变更的唯一入口。
 *
 * <p>灾难砸掉的地形、将来地图重置写的方块，都要经这里落地。收敛到一处有两个理由：
 * 高度图缓存需要跟着每次变更增量更新，而录像需要知道「哪些方块是插件改的」——
 * 这两件事都只能从同一个入口拿到。</p>
 *
 * <p>变更不立即广播，攒到 tick 末尾按来源合并成一批再发（见 {@link ChangeBatch}）。
 * 一波地陷就是几百个方块，逐条发事件会把监听方拖死。</p>
 *
 * <p>只在主线程调用。</p>
 */
public final class BlockWriter {

    /** 来源标识：灾难改方块时用灾种 id，地图施工另起一个。 */
    public static final String CAUSE_MAP = "map";

    private final DisasterPlugin plugin;
    private final ChangeBatch batch = new ChangeBatch();

    private BukkitTask flusher;

    /** 当前对局标识，随事件发出去供录像与上报关联。不在对局中时为空串。 */
    private String matchId = "";

    public BlockWriter(DisasterPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // 每 tick 冲一次。这个任务本身只是看看批次空不空，没有全图操作。
        this.flusher = Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 1L, 1L);
    }

    public void stop() {
        if (flusher != null) {
            flusher.cancel();
            flusher = null;
        }
        flush();
    }

    public void matchId(String matchId) {
        this.matchId = matchId == null ? "" : matchId;
    }

    public String matchId() {
        return matchId;
    }

    /** 攒着还没广播的变更条数。诊断命令用得上。 */
    public int pending() {
        return batch.size();
    }

    /**
     * 改一个方块。
     *
     * <p>材质没变就直接返回，不产生事件——形状计算难免重复覆盖同一格，
     * 把「改成它本来就是这个材质」也算进回放里纯属噪声。</p>
     *
     * @param applyPhysics 是否让邻块跟着反应。灾难砸地形一律用 false，
     *                     否则沙子会连锁塌方，一格里能滚出几百格变更
     */
    public boolean set(Block block, Material material, boolean applyPhysics, String cause) {
        if (block == null || material == null) {
            return false;
        }
        Material from = block.getType();
        if (from == material) {
            return false;
        }
        block.setType(material, applyPhysics);
        record(block.getWorld(), block.getX(), block.getY(), block.getZ(), from, material, cause);
        return true;
    }

    /** 按坐标改一个方块。坐标落在世界外时什么都不做。 */
    public boolean set(World world, int x, int y, int z, Material material, String cause) {
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        return set(world.getBlockAt(x, y, z), material, false, cause);
    }

    /** 批量改。返回真正改动的方块数。 */
    public int setAll(World world, List<BlockPos> positions, Material material, String cause) {
        if (world == null || positions == null || positions.isEmpty()) {
            return 0;
        }
        int changed = 0;
        for (BlockPos pos : positions) {
            if (set(world, pos.x(), pos.y(), pos.z(), material, cause)) {
                changed++;
            }
        }
        return changed;
    }

    /** 把攒下的变更按来源合并广播出去。 */
    public void flush() {
        Map<String, List<BlockChange>> drained = batch.drainAll();
        if (drained.isEmpty()) {
            return;
        }
        String owner = matchId;
        for (Map.Entry<String, List<BlockChange>> entry : drained.entrySet()) {
            Bukkit.getPluginManager().callEvent(
                    new DisasterBlockChangedEvent(owner, entry.getKey(), entry.getValue()));
        }
    }

    private void record(World world, int x, int y, int z, Material from, Material to, String cause) {
        boolean full = batch.record(cause, new BlockChange(
                world.getName(), x, y, z, from.name(), to.name()));
        // 一波大灾难可能超出单批上限，先冲一次免得堆着
        if (full) {
            flush();
        }
    }
}
