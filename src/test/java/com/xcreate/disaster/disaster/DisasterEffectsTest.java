package com.xcreate.disaster.disaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 效果分发表的回归：内置登记、大小写、未实现灾种静默跳过。 */
class DisasterEffectsTest {

    @Test
    void 内置里有地陷() {
        assertTrue(DisasterEffects.builtin().has(SinkholeEffect.ID));
    }

    @Test
    void 没实现效果的灾种查不到() {
        // 掷到还没写效果的灾种时只落点不动方块，不该在这里报错
        assertFalse(DisasterEffects.builtin().has("no_such_disaster"));
        assertTrue(DisasterEffects.builtin().find("no_such_disaster").isEmpty());
    }

    @Test
    void 做好的灾种都登记在表里() {
        DisasterEffects effects = DisasterEffects.builtin();
        for (String id : List.of(MeteorEffect.ID, LightningEffect.ID, SinkholeEffect.ID,
                TornadoEffect.ID, FloodEffect.ID, AcidRainEffect.ID, ZombieHordeEffect.ID,
                FloorIsLavaEffect.ID, AnvilRainEffect.ID, PurgeEffect.ID)) {
            assertTrue(effects.has(id), id);
        }
    }

    @Test
    void 每个内置灾种都有对应效果() {
        DisasterEffects effects = DisasterEffects.builtin();
        for (String id : BuiltinDisasters.IDS) {
            assertTrue(effects.has(id), "灾种 " + id + " 没有实现效果");
        }
    }

    @Test
    void 查标识不区分大小写() {
        assertTrue(DisasterEffects.builtin().has("SINKHOLE"));
        assertTrue(DisasterEffects.builtin().find("Sinkhole").isPresent());
    }

    @Test
    void 空标识登记不进去也查不出来() {
        DisasterEffects effects = DisasterEffects.builtin();
        int before = effects.ids().size();

        effects.register(null);
        effects.register(new DisasterEffect() {
            @Override
            public String id() {
                return "  ";
            }

            @Override
            public int apply(EffectContext context, java.util.List<com.xcreate.disaster.map.MapPoint> points) {
                return 0;
            }
        });

        assertEquals(before, effects.ids().size());
        assertFalse(effects.has(null));
        assertTrue(effects.find(null).isEmpty());
    }
}
