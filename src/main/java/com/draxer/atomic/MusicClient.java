package com.draxer.atomic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Música por zona: Midgar, Oriana, el Santuario, la Ciudad sin Ley y las peleas de jefe.
 * Mientras suena, la música de vanilla se calla. Se controla con el volumen de "Música".
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class MusicClient {
    private MusicClient() {}

    private static SoundEvent playing;
    private static SoundInstance instance;

    private static SoundEvent wanted() {
        if (ZonePayload.has(ZonePayload.BOSS)) return Music.BOSS.value();
        if (ZonePayload.has(ZonePayload.LAWLESS)) return Music.LAWLESS.value();
        if (ZonePayload.has(ZonePayload.SANCTUARY)) return Music.SANCTUARY.value();
        if (ZonePayload.has(ZonePayload.ORIANA)) return Music.ORIANA.value();
        if (ZonePayload.has(ZonePayload.MIDGAR)) return Music.MIDGAR.value();
        return null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stop(mc);
            return;
        }
        if (mc.level.getGameTime() % 10 != 0) return;
        SoundEvent want = wanted();
        if (want != playing || (instance != null && !mc.getSoundManager().isActive(instance))) {
            stop(mc);
            if (want != null) {
                instance = new SimpleSoundInstance(want.location(), SoundSource.MUSIC, 0.7F, 1.0F, RandomSource.create(),
                        true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
                mc.getSoundManager().play(instance);
                playing = want;
            }
        }
        if (playing != null) mc.getMusicManager().stopPlaying();
    }

    private static void stop(Minecraft mc) {
        if (instance != null) mc.getSoundManager().stop(instance);
        instance = null;
        playing = null;
    }
}
