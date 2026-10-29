package com.draxer.atomic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Las habilidades rápidas de la espada: Látigo, Corte instantáneo y Lluvia de cuchillas. */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class SlimeSkills {
    private SlimeSkills() {}

    private static final DustParticleOptions SLIME = new DustParticleOptions(0x2A1048, 1.6F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(0x8A3BFF, 1.2F);
    private static final DustParticleOptions CYAN = new DustParticleOptions(0x5FE6FF, 1.0F);

    // ---------------------------------------------------------------- Látigo

    /** La hoja se estira en línea recta y golpea todo lo que hay en ella, hasta chocar con un bloque. */
    public static void whip(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        float mult = Magic.consumeCompression(player) * Magic.power(player);
        Vec3 end = start.add(look.scale(AtomicConfig.whipRange() * Magic.reach(player)));
        BlockHitResult wall = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        double length = start.distanceTo(end);

        for (Entity target : alongLine(level, player, start, end, 1.2)) {
            target.hurtServer(level, source(level, player), AtomicConfig.whipDamage() * mult);
            // Lo acerca un poco, como si el látigo lo atrapara.
            Vec3 pull = player.position().subtract(target.position()).normalize().scale(0.6);
            target.push(pull.x, 0.25, pull.z);
            target.hurtMarked = true;
        }
        for (double d = 1.0; d < length; d += 0.35) {
            Vec3 p = start.add(look.scale(d)).add(0, -0.25, 0);
            level.sendParticles(d % 1.4 < 0.35 ? VIOLET : SLIME, true, false, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_ATTACK,
                SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    // ---------------------------------------------------------------- Corte instantáneo

    /** Avanza de golpe hacia donde miras y corta todo lo que atraviesa. */
    public static void dash(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, Math.max(-0.3, Math.min(0.3, look.y)), look.z).normalize();
        Vec3 from = player.position().add(0, 0.5, 0);
        float mult = Magic.consumeCompression(player) * Magic.power(player);
        Vec3 to = from.add(flat.scale(AtomicConfig.dashDistance() * Magic.reach(player)));
        BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            to = wall.getLocation().subtract(flat.scale(0.6));
        }
        for (Entity target : alongLine(level, player, from, to, 1.4)) {
            target.hurtServer(level, source(level, player), AtomicConfig.dashDamage() * mult);
        }
        double length = from.distanceTo(to);
        for (double d = 0; d < length; d += 0.4) {
            Vec3 p = from.add(flat.scale(d));
            level.sendParticles(VIOLET, true, false, p.x, p.y + 0.5, p.z, 2, 0.15, 0.4, 0.15, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, false, p.x, p.y + 0.6, p.z, 1, 0, 0, 0, 0);
        }
        player.teleportTo(to.x, to.y - 0.5, to.z);
        player.fallDistance = 0;
        level.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    // ---------------------------------------------------------------- Lluvia de cuchillas

    private static final int RAIN_WAVES = 6;
    private static final int RAIN_GAP = 5;
    private static final List<Rain> RAINS = new ArrayList<>();

    private record Rain(ServerLevel level, UUID caster, Vec3 center, int radius, float damage, int[] age) {}

    /** Marca un punto a la vista y hace caer oleadas de agujas de slime sobre él. Falla si no apuntas a nada. */
    public static boolean bladeRain(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(32));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return false;
        Vec3 center = hit.getLocation();
        float mult = Magic.consumeCompression(player) * Magic.power(player);
        RAINS.add(new Rain(level, player.getUUID(), center, Math.round(AtomicConfig.rainRadius() * Magic.reach(player)),
                AtomicConfig.rainDamage() * mult, new int[] {0}));
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.5F, 0.6F);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (RAINS.isEmpty()) return;
        Iterator<Rain> it = RAINS.iterator();
        while (it.hasNext()) {
            Rain rain = it.next();
            int age = ++rain.age()[0];
            tickRain(rain, age);
            if (age >= RAIN_WAVES * RAIN_GAP + 10) it.remove();
        }
    }

    private static void tickRain(Rain rain, int age) {
        ServerLevel level = rain.level();
        Vec3 c = rain.center();
        int r = rain.radius();
        // Círculo de aviso en el suelo durante toda la lluvia.
        if (age % 2 == 0) {
            for (int i = 0; i < 28; i++) {
                double a = i * Math.PI * 2 / 28 + age * 0.05;
                level.sendParticles(VIOLET, true, false, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        if (age < 10) return;
        int wave = age - 10;
        // Agujas cayendo desde arriba (visual continuo).
        for (int i = 0; i < 10; i++) {
            double x = c.x + (level.getRandom().nextDouble() * 2 - 1) * r;
            double z = c.z + (level.getRandom().nextDouble() * 2 - 1) * r;
            if ((x - c.x) * (x - c.x) + (z - c.z) * (z - c.z) > r * r) continue;
            for (int h = 0; h < 4; h++) {
                level.sendParticles(h == 0 ? CYAN : SLIME, true, false, x, c.y + 1 + h * 0.6 + level.getRandom().nextDouble() * 4,
                        z, 1, 0, 0, 0, 0);
            }
        }
        if (wave % RAIN_GAP != 0 || wave / RAIN_GAP >= RAIN_WAVES) return;
        // Una oleada de impacto.
        Entity caster = level.getEntity(rain.caster());
        AABB area = new AABB(c.x - r, c.y - 2, c.z - r, c.x + r, c.y + 4, c.z + r);
        DamageSource src = caster != null ? level.damageSources().indirectMagic(caster, caster) : level.damageSources().magic();
        for (Entity target : level.getEntities((Entity) null, area)) {
            if (!(target instanceof LivingEntity) || isFriend(target, caster)) continue;
            double dx = target.getX() - c.x;
            double dz = target.getZ() - c.z;
            if (dx * dx + dz * dz > r * r) continue;
            target.invulnerableTime = 0; // cada oleada pega, aunque sean seguidas
            target.hurtServer(level, src, rain.damage());
        }
        level.sendParticles(ParticleTypes.CRIT, true, false, c.x, c.y + 0.3, c.z, 30, r * 0.6, 0.1, r * 0.6, 0.1);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.6F + wave * 0.02F);
    }

    // ---------------------------------------------------------------- utilidades

    private static DamageSource source(ServerLevel level, ServerPlayer player) {
        return level.damageSources().playerAttack(player);
    }

    /** Entidades vivas (que no sean aliadas) cerca de la línea entre dos puntos. */
    private static List<Entity> alongLine(ServerLevel level, Player player, Vec3 from, Vec3 to, double width) {
        AABB box = new AABB(from, to).inflate(width);
        List<Entity> found = new ArrayList<>();
        Vec3 dir = to.subtract(from);
        double lenSq = Math.max(1.0E-6, dir.lengthSqr());
        for (Entity e : level.getEntities(player, box)) {
            if (!(e instanceof LivingEntity) || !e.isAlive() || isFriend(e, player)) continue;
            Vec3 mid = e.getBoundingBox().getCenter();
            double t = Math.max(0, Math.min(1, mid.subtract(from).dot(dir) / lenSq));
            Vec3 closest = from.add(dir.scale(t));
            if (mid.distanceTo(closest) <= width + e.getBbWidth() * 0.5) found.add(e);
        }
        return found;
    }

    static boolean isFriend(Entity entity, Entity caster) {
        if (caster == null) return false;
        if (entity == caster) return true;
        if (entity instanceof TamableAnimal pet && caster instanceof LivingEntity owner && pet.isOwnedBy(owner)) return true;
        if (entity instanceof Player p && p.isCreative()) return true;
        return entity.isAlliedTo(caster);
    }
}
