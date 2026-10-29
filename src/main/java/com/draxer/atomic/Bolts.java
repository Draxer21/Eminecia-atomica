package com.draxer.atomic;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Rayo mágico instantáneo: una línea de partículas y daño directo al objetivo. */
public final class Bolts {
    private Bolts() {}

    public static void fire(ServerLevel level, Mob shooter, LivingEntity target, float damage, int color, SoundEvent sound) {
        Vec3 from = shooter.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 step = to.subtract(from);
        double length = step.length();
        DustParticleOptions dust = new DustParticleOptions(color, 1.2F);
        for (double d = 0; d < length; d += 0.4) {
            Vec3 p = from.add(step.scale(d / length));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        target.hurtServer(level, shooter.damageSources().indirectMagic(shooter, shooter), damage);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
        shooter.swing(InteractionHand.MAIN_HAND, true);
    }
}
