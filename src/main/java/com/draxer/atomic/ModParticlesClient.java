package com.draxer.atomic;

import net.minecraft.client.particle.CritParticle;
import net.minecraft.client.particle.EndRodParticle;
import net.minecraft.client.particle.GlowParticle;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.SmokeParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Une cada partícula propia con un movimiento de Minecraft y sus propias texturas. */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID, value = Dist.CLIENT)
public final class ModParticlesClient {
    private ModParticlesClient() {}

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.VIOLET_SPARK.value(), EndRodParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHADOW_SMOKE.value(), SmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOD_DROP.value(), CritParticle.Provider::new);
        event.registerSpriteSet(ModParticles.LIGHT_SHARD.value(), GlowParticle.ElectricSparkProvider::new);
        event.registerSpriteSet(ModParticles.ENERGY_PETAL.value(), PortalParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHOCKWAVE.value(), HugeExplosionParticle.Provider::new);
    }
}
