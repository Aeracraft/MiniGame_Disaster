package com.xcreate.disaster.disaster;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Set;

/**
 * 灾难能不能动这一格。
 *
 * <p>只吃实心方块。液体不算实心，因此自动被挡在外面——挖穿一层薄地面会把整条河、
 * 整个岩浆湖放出来，那已经不是灾难本身的效果了。</p>
 *
 * <p>另外避开几种一动就出事的：基岩挖穿露出虚空，传送门框架挖掉会让已激活的门熄火。</p>
 */
final class DigRule {

    private static final Set<Material> KEEP = Set.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.END_PORTAL,
            Material.END_PORTAL_FRAME,
            Material.NETHER_PORTAL,
            Material.STRUCTURE_VOID,
            Material.LIGHT);

    private DigRule() {
    }

    static boolean diggable(Block block) {
        Material type = block.getType();
        if (block.isLiquid() || !type.isSolid()) {
            return false;
        }
        return !KEEP.contains(type);
    }
}
