package com.xcreate.disaster.disaster;

import com.xcreate.disaster.map.MapBounds;
import com.xcreate.disaster.world.BlockWriter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 灾难落到地上时能碰到的东西。
 *
 * @param world        副本世界
 * @param terrain      地表读取入口。真实实现读世界，测试实现读一张表
 * @param blocks       方块变更的唯一入口。改方块一律经它，别直接 setType
 * @param bounds       地图边界。全图型灾难（酸雨、洪水）要在里面撒列
 * @param participants 还活着的玩家。跟随玩家的灾种（脚下岩浆、龙卷风）要用
 * @param random       本局的随机源，与掷骰共用。形状里的抖动一律从它取，
 *                     否则固定种子复现不了一模一样的坑
 * @param definition   本灾种的定义，自由参数从 {@code definition.options()} 里取
 */
public record EffectContext(World world, SpawnTerrain terrain, BlockWriter blocks,
                            MapBounds bounds, List<UUID> participants, Random random,
                            DisasterDefinition definition) {

    public EffectContext {
        participants = participants == null ? List.of() : List.copyOf(participants);
        random = random == null ? new Random() : random;
    }

    /** 还活着的玩家本体。中途下线的会被跳过，调用方不必再判空。 */
    public List<Player> players() {
        List<Player> online = new ArrayList<>(participants.size());
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    public boolean hasBounds() {
        return bounds != null;
    }
}
