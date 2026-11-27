package com.draxer.atomic;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Partículas propias del mod (texturas en textures/particle, generadas por tools/particles.py).
 * Cada una reutiliza el movimiento de una partícula de Minecraft, pero con su propio dibujo.
 */
public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, DraxAtomic.MOD_ID);

    /** Chispa violeta de magia que flota y se apaga (movimiento de vara del End). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VIOLET_SPARK = simple("violet_spark");
    /** Humo de sombra que sube (movimiento de humo). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHADOW_SMOKE = simple("shadow_smoke");
    /** Gota de sangre que salta y cae (movimiento de golpe crítico). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_DROP = simple("blood_drop");
    /** Esquirla de luz: las grietas del cuerpo en la sobrecompresión (chispa eléctrica). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LIGHT_SHARD = simple("light_shard");
    /** Pétalo de energía que es absorbido hacia el que carga (movimiento de portal). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ENERGY_PETAL = simple("energy_petal");
    /** Anillo de onda expansiva del Atomic (animación de explosión). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHOCKWAVE = simple("shockwave");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name) {
        // Siempre visibles: son parte de técnicas grandes que se ven de lejos.
        return PARTICLES.register(name, () -> new SimpleParticleType(true) {});
    }
}
