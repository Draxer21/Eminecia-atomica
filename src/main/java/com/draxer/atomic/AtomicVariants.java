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
 *   <li><b>All-Range Atomic:</b> se paga con poder comprimido. Marca a cada enemigo en un radio enorme,
 *   hace estallar una explosión sobre cada uno y termina en una esfera gigante que arrasa todo y
 *   empuja el agua hacia fuera.</li>
 *   <li><b>Recovery Atomic:</b> una esfera de luz que cura por completo al lanzador, a sus aliados
 *   y a los demás jugadores cercanos, y les quita los efectos negativos.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class AtomicVariants {
    private AtomicVariants() {}

    private static final int MARK_TICKS = 16;

    private record Strike(ServerLevel level, UUID caster, LivingEntity target, int radius, float damage, int[] delay) {}

    private static final List<Strike> STRIKES = new ArrayList<>();

    // ---------------------------------------------------------------- All-Range Atomic

    /** Radio del All-Range según los niveles de compresión (mínimo 1): 64 → 86, 109, 131, 154… (tope 160). */
    public static int allRangeRadius(Player player, int compression) {
        int c = Math.max(1, compression);
        return Math.min(160, Math.round(AtomicConfig.allRangeRadius() * Magic.reach(player) * (1.0F + 0.35F * c)));
    }

    private static List<LivingEntity> targets(ServerLevel level, ServerPlayer player, int reach) {
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                e -> e.isAlive() && isHostileTo(e, player) && e.distanceToSqr(player) <= (double) reach * reach);
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        int max = AtomicConfig.allRangeMaxTargets();
        return targets.size() > max ? new ArrayList<>(targets.subList(0, max)) : targets;
    }

    /** Durante la carga: hilos de luz desde el lanzador hasta cada enemigo que quedará marcado. */
    public static void lockOn(ServerLevel level, ServerPlayer player) {
        int reach = allRangeRadius(player, Magic.compressionLevel(player));
        Vec3 from = player.position().add(0, 1.2, 0);
        DustParticleOptions thread = new DustParticleOptions(0xB07CFF, 0.9F);
        for (LivingEntity target : targets(level, player, reach)) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 d = to.subtract(from);
            double len = d.length();
            double step = Math.max(1.5, len / 40.0);
            for (double t = 1.0; t < len; t += step) {
                Vec3 p = from.add(d.scale(t / len));
                level.sendParticles(thread, true, false, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            level.sendParticles(new DustParticleOptions(0x5FE6FF, 1.2F), true, false, to.x, to.y, to.z, 4, 0.3, 0.3, 0.3, 0);
        }
    }

    /**
     * El All-Range Atomic, pagado con poder comprimido: cada enemigo marcado recibe su propia
     * explosión casi a la vez y, enseguida, todo estalla en una esfera enorme centrada en el
     * lanzador que borra el terreno (como el Santuario) y empuja el agua cercana hacia fuera.
     */
    public static int allRange(ServerLevel level, ServerPlayer player) {
        int compression = Magic.compressionLevel(player);
        int reach = allRangeRadius(player, compression);
        List<LivingEntity> targets = targets(level, player, reach);
        float comp = Magic.consumeCompression(player);
        float power = Magic.power(player);
        int blastRadius = Math.round(AtomicConfig.allRangeBlastRadius() * (1.0F + (comp - 1.0F) * 0.4F));
        float damage = AtomicConfig.allRangeDamage() * comp * power;
        Vec3 center = player.position().add(0, 1.0, 0);

        // Destello inicial: rayos desde el lanzador hacia todas direcciones.
        DustParticleOptions ray = new DustParticleOptions(0xD9B8FF, 2.0F);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            double up = (i % 3 - 1) * 0.25;
            for (double t = 2; t < Math.min(reach, 60); t += 2.5) {
                level.sendParticles(ray, true, false, center.x + Math.cos(a) * t, center.y + up * t, center.z + Math.sin(a) * t, 1, 0, 0, 0, 0);
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            STRIKES.add(new Strike(level, player.getUUID(), targets.get(i), blastRadius, damage, new int[] {MARK_TICKS + i}));
        }
        float finalDamage = AtomicConfig.allRangeFinalDamage() * comp * power;
        FINALES.add(new Finale(level, player.getUUID(), center, reach, finalDamage, new int[] {MARK_TICKS + targets.size() + 8}));
        return Math.max(1, targets.size());
    }

    private record Finale(ServerLevel level, UUID caster, Vec3 center, int radius, float damage, int[] delay) {}

    private static final List<Finale> FINALES = new ArrayList<>();

    private static void finaleTick() {
        Iterator<Finale> it = FINALES.iterator();
        while (it.hasNext()) {
            Finale f = it.next();
            int left = --f.delay()[0];
            ServerLevel level = f.level();
            if (left > 0) {
                // La luz se concentra en el centro antes de estallar.
                if (left % 2 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, true, true, f.center().x, f.center().y, f.center().z,
                            12, 1.5, 1.5, 1.5, 0.15);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, f.center().x, f.center().y, f.center().z,
                            30, 6, 6, 6, 0.4);
                }
                continue;
            }
            it.remove();
            if (!(level.getEntity(f.caster()) instanceof ServerPlayer caster)) continue;
            long[] water = AtomicConfig.allRangeWave() ? WaterWave.measure(level, f.center(), f.radius()) : null;
            AtomicBlast.detonateAt(level, caster, f.center(), f.radius(), f.damage(), AtomicConfig.allRangeFinalBreaks(), true);
            if (water != null) WaterWave.start(level, f.center(), f.radius(), water, AtomicBlast.EXPAND_TICKS + 4);
        }
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
        if (!FINALES.isEmpty()) finaleTick();
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
            AtomicBlast.detonateAt(strike.level(), caster, at, strike.radius(), strike.damage(), AtomicConfig.allRangeBreaksBlocks());
        }
    }

    // ---------------------------------------------------------------- Recovery Atomic

    public static void recovery(ServerLevel level, ServerPlayer player) {
        int reach = Math.round(AtomicConfig.recoveryRadius() * Magic.reach(player));
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
