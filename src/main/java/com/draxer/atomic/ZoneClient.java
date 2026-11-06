package com.draxer.atomic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Ambiente por zona: durante la Luna Roja el horizonte y la niebla se tiñen de rojo y cae ceniza;
 * de noche, la Ciudad sin Ley se cubre de una niebla oscura y violácea.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class ZoneClient {
    private ZoneClient() {}

    /** Mezcla actual (0..1) de cada tinte, para que entre y salga suave. */
    private static float red;
    private static float dark;

    private static final DustParticleOptions ASH = new DustParticleOptions(0xB0101E, 0.8F);
    private static final DustParticleOptions SOOT = new DustParticleOptions(0x1A1020, 1.0F);

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            red = 0;
            dark = 0;
            return;
        }
        red = Mth.approach(red, ZonePayload.has(ZonePayload.RED_MOON) ? 1.0F : 0.0F, 0.02F);
        dark = Mth.approach(dark, ZonePayload.has(ZonePayload.LAWLESS_NIGHT) ? 1.0F : 0.0F, 0.02F);
        Player p = mc.player;
        RandomSource r = mc.level.getRandom();
        if (red > 0.3F) {
            for (int i = 0; i < (int) (6 * red); i++) {
                mc.level.addParticle(ASH, p.getX() + (r.nextDouble() - 0.5) * 24, p.getY() + 6 + r.nextDouble() * 6,
                        p.getZ() + (r.nextDouble() - 0.5) * 24, 0, -0.05, 0);
            }
        }
        if (dark > 0.3F && r.nextInt(3) == 0) {
            mc.level.addParticle(r.nextBoolean() ? SOOT : ParticleTypes.ASH, p.getX() + (r.nextDouble() - 0.5) * 16,
                    p.getY() + r.nextDouble() * 4, p.getZ() + (r.nextDouble() - 0.5) * 16, 0, 0.01, 0);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (red <= 0 && dark <= 0) return;
        float rr = event.getRed();
        float gg = event.getGreen();
        float bb = event.getBlue();
        // Niebla nocturna de la Ciudad sin Ley: casi negra con un toque violeta.
        rr = Mth.lerp(dark * 0.8F, rr, 0.10F);
        gg = Mth.lerp(dark * 0.8F, gg, 0.05F);
        bb = Mth.lerp(dark * 0.8F, bb, 0.14F);
        // Luna Roja: rojo sangre.
        rr = Mth.lerp(red * 0.85F, rr, 0.62F);
        gg = Mth.lerp(red * 0.85F, gg, 0.04F);
        bb = Mth.lerp(red * 0.85F, bb, 0.06F);
        event.setRed(rr);
        event.setGreen(gg);
        event.setBlue(bb);
    }
}
