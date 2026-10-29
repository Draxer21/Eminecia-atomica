package com.draxer.atomic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Las variantes del Atomic:
 * <ul>
 *   <li><b>All-Range Atomic:</b> marca a cada enemigo en un radio enorme y hace caer una pequeña
 *   explosión atómica sobre cada uno, una tras otra. No rompe el terreno por defecto.</li>
 *   <li><b>Recovery Atomic:</b> una esfera de luz que cura por completo al lanzador, a sus aliados
 *   y a los demás jugadores cercanos, y les quita los efectos negativos.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class AtomicVariants {
    private AtomicVariants() {}

    private static final int STRIKE_GAP = 3;
    private static final int MARK_TICKS = 16;

    private record Strike(ServerLevel level, UUID caster, LivingEntity target, int[] delay) {}

    private static final List<Strike> STRIKES = new ArrayList<>();

    // ---------------------------------------------------------------- All-Range Atomic

    /** Devuelve cuántos objetivos encontró (0 = no se lanza y no gasta poder). */
    public static int allRange(ServerLevel level, ServerPlayer player) {
        int reach = AtomicConfig.allRangeRadius();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                e -> e.isAlive() && isHostileTo(e, player));
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        int max = AtomicConfig.allRangeMaxTargets();
        if (targets.size() > max) targets = new ArrayList<>(targets.subList(0, max));
        for (int i = 0; i < targets.size(); i++) {
            STRIKES.add(new Strike(level, player.getUUID(), targets.get(i), new int[] {MARK_TICKS + i * STRIKE_GAP}));
        }
        return targets.size();
    }

    /** Enemigos: monstruos, criaturas que atacan al lanzador o a sus aliadas, y a quien él atacó. */
    private static boolean isHostileTo(LivingEntity e, Player player) {
        if (SlimeSkills.isFriend(e, player) || e instanceof Player) return false;
        if (e instanceof Enemy) return true;
        if (e instanceof Mob mob && mob.getTarget() != null
                && (mob.getTarget() == player || SlimeSkills.isFriend(mob.getTarget(), player))) return true;
        return e == player.getLastHurtMob();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (STRIKES.isEmpty()) return;
        Iterator<Strike> it = STRIKES.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            LivingEntity target = strike.target();
            int delay = --strike.delay()[0];
            if (!target.isAlive() || target.level() != strike.level()) {
                it.remove();
                continue;
            }
            // Marca: un pilar de luz violeta baja del cielo sobre el objetivo.
            if (delay % 2 == 0) {
                ServerLevel level = strike.level();
                double top = Math.min(10, 2 + delay * 0.6);
                for (double h = 0; h < top; h += 0.7) {
                    level.sendParticles(new DustParticleOptions(0x8A3BFF, 1.6F), true, false,
                            target.getX(), target.getY() + h, target.getZ(), 1, 0.08, 0.05, 0.08, 0);
                }
                level.sendParticles(new DustParticleOptions(0x5FE6FF, 1.0F), true, false,
                        target.getX(), target.getY() + 0.1, target.getZ(), 6, 0.6, 0, 0.6, 0);
            }
            if (delay > 0) continue;
            it.remove();
            if (!(strike.level().getEntity(strike.caster()) instanceof ServerPlayer caster)) continue;
            Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
            AtomicBlast.detonateAt(strike.level(), caster, at, AtomicConfig.allRangeBlastRadius(),
                    AtomicConfig.allRangeDamage(), AtomicConfig.allRangeBreaksBlocks());
        }
    }

    // ---------------------------------------------------------------- Recovery Atomic

    public static void recovery(ServerLevel level, ServerPlayer player) {
        int reach = AtomicConfig.recoveryRadius();
        Vec3 c = player.position().add(0, 1, 0);
        AtomicPayload payload = new AtomicPayload(c.x, c.y, c.z, reach, AtomicPayload.RECOVERY, false);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(c) < 128 * 128) PacketDistributor.sendToPlayer(viewer, payload);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 1.4F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.6F);
        List<LivingEntity> healed = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                e -> e.isAlive() && e.distanceToSqr(player) <= reach * reach
                        && (e == player || e instanceof Player || SlimeSkills.isFriend(e, player)));
        for (LivingEntity e : healed) {
            e.setHealth(e.getMaxHealth());
            List<Holder<MobEffect>> bad = new ArrayList<>();
            for (MobEffectInstance effect : e.getActiveEffects()) {
                if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(effect.getEffect());
            }
            for (Holder<MobEffect> effect : bad) e.removeEffect(effect);
            e.clearFire();
            e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
            level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 5, 0.4, 0.3, 0.4, 0);
            level.sendParticles(new DustParticleOptions(AtomicPayload.RECOVERY, 1.4F), e.getX(), e.getY() + 1, e.getZ(),
                    20, 0.4, 0.8, 0.4, 0);
        }
    }
}
