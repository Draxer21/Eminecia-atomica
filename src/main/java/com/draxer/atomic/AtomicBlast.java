package com.draxer.atomic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * La explosión atómica. Una esfera de luz crece desde quien la lanzó durante
 * {@link #EXPAND_TICKS} ticks: todo lo que alcanza el frente de la onda recibe el golpe una
 * sola vez, y (si está activado) el terreno dentro de la esfera desaparece capa a capa sin
 * soltar ítems, para no congelar el servidor. Quien la lanza y sus aliados quedan a salvo.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class AtomicBlast {
    /** Duración de la expansión de la esfera, en ticks (también la usa el cliente). */
    public static final int EXPAND_TICKS = 24;
    /** Máximo de bloques borrados por tick; lo que sobre se termina en los ticks siguientes. */
    private static final int MAX_REMOVALS_PER_TICK = 40_000;

    private static final List<AtomicBlast> ACTIVE = new ArrayList<>();

    private final ServerLevel level;
    private final UUID casterId;
    private final Vec3 center;
    private final int radius;
    private final float damage;
    private final boolean carve;
    private final boolean protectContainers;
    private final Set<UUID> hit = new HashSet<>();
    private final List<BlockPos> pending = new ArrayList<>();
    private int age;
    /** Radio (al cuadrado) ya procesado por el tallado del cráter. */
    private double carvedSq = -1;

    private final boolean big;

    private AtomicBlast(ServerLevel level, UUID casterId, Vec3 center, int radius, float damage, boolean carve, boolean big) {
        this.level = level;
        this.casterId = casterId;
        this.center = center;
        this.radius = radius;
        this.damage = damage;
        this.carve = carve;
        this.protectContainers = AtomicConfig.protectContainers();
        this.big = big;
    }

    /** El Atomic normal, centrado en quien lo lanza. */
    public static void detonate(ServerLevel level, ServerPlayer caster) {
        // La compresión agranda la esfera y multiplica el daño; el árbol de habilidades también.
        float comp = Magic.consumeCompression(caster);
        int radius = Math.min(96, Math.round(AtomicConfig.radius() * Magic.reach(caster) * (1.0F + (comp - 1.0F) * 0.4F)));
        AtomicBlast blast = new AtomicBlast(level, caster.getUUID(), caster.position().add(0, 1.0, 0),
                radius, AtomicConfig.damage() * comp * Magic.power(caster), AtomicConfig.destroyBlocks(), true);
        ACTIVE.add(blast);
        blast.flash();
    }

    /** Una explosión atómica más pequeña en cualquier punto (la usa el All-Range Atomic). */
    public static void detonateAt(ServerLevel level, ServerPlayer caster, Vec3 center, int radius, float damage, boolean carve) {
        detonateAt(level, caster, center, radius, damage, carve, false);
    }

    /** Con {@code big} = true se comporta como el Atomic completo (destello, sonido lejano, columna de luz). */
    public static void detonateAt(ServerLevel level, ServerPlayer caster, Vec3 center, int radius, float damage, boolean carve, boolean big) {
        AtomicBlast blast = new AtomicBlast(level, caster.getUUID(), center, radius, damage, carve, big);
        ACTIVE.add(blast);
        blast.flash();
    }

    // ---------------------------------------------------------------- el instante inicial

    private void flash() {
        double x = center.x;
        double y = center.y;
        double z = center.z;
        AtomicPayload payload = new AtomicPayload(x, y, z, radius, AtomicPayload.VIOLET, big);
        for (ServerPlayer player : level.players()) {
            double distance = Math.sqrt(player.distanceToSqr(x, y, z));
            if (distance > Math.max(radius * 10, 96)) continue;
            PacketDistributor.sendToPlayer(player, payload);
            if (!big) continue;
            // Se reproduce junto a cada jugador para que se oiga a cualquier distancia.
            float volume = (float) Mth.clamp(3.0 - distance / (radius * 3.0), 0.5, 3.0);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.PLAYERS, volume, 0.5F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, volume, 0.4F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER, volume, 0.6F);
        }
        if (!big) level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.7F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, x, y, z, 6, 1.0, 1.0, 1.0, 0.0);
    }

    // ---------------------------------------------------------------- ticks

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<AtomicBlast> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (!it.next().tick()) it.remove();
        }
    }

    /** Un paso; false cuando terminó. */
    private boolean tick() {
        age++;
        double current = radius * ease(Math.min(1.0, age / (double) EXPAND_TICKS));
        if (age <= EXPAND_TICKS + 1) {
            shockFront(current);
            if (carve) collectShell(current);
        }
        if (!pending.isEmpty()) removeSome();
        return age <= EXPAND_TICKS + 1 || !pending.isEmpty();
    }

    /** Arranca rápido y frena al final, como una onda que pierde fuerza. */
    private static double ease(double t) {
        return 1.0 - Math.pow(1.0 - t, 3);
    }

    /** Todo lo que está dentro del radio actual y aún no fue golpeado recibe el impacto. */
    private void shockFront(double current) {
        Entity caster = level.getEntity(casterId);
        double reach = current + 1.0;
        AABB area = new AABB(center.x - reach, center.y - reach, center.z - reach,
                center.x + reach, center.y + reach, center.z + reach);
        DamageSource source = caster != null ? level.damageSources().sonicBoom(caster) : level.damageSources().magic();
        for (Entity entity : level.getEntities((Entity) null, area)) {
            if (hit.contains(entity.getUUID())) continue;
            double distance = Math.sqrt(entity.distanceToSqr(center));
            if (distance > reach) continue;
            hit.add(entity.getUUID());
            if (isSafe(entity, caster)) continue;
            if (entity instanceof ItemEntity) {
                entity.discard();
                continue;
            }
            double strength = 1.0 - 0.5 * Math.min(1.0, distance / radius);
            entity.hurtServer(level, source, (float) (damage * strength));
            Vec3 away = entity.position().subtract(center);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(0, 1, 0);
            Vec3 push = away.normalize().scale(2.5 * strength).add(0, 0.6 * strength, 0);
            entity.push(push.x, push.y, push.z);
            entity.hurtMarked = true;
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            }
        }
    }

    /** Quien lanzó el ataque, sus mascotas y su equipo no reciben daño. */
    private static boolean isSafe(Entity entity, Entity caster) {
        if (caster == null) return false;
        if (entity == caster) return true;
        if (entity instanceof TamableAnimal pet && caster instanceof LivingEntity owner && pet.isOwnedBy(owner)) return true;
        if (entity instanceof Player player && player.isCreative()) return true;
        return entity.isAlliedTo(caster);
    }

    /** Anota los bloques entre el radio procesado y el actual para borrarlos. */
    private void collectShell(double current) {
        double outerSq = current * current;
        if (outerSq <= carvedSq) return;
        int r = Mth.ceil(current);
        int cx = Mth.floor(center.x);
        int cy = Mth.floor(center.y);
        int cz = Mth.floor(center.z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                int y = cy + dy;
                if (y < level.getMinY() || y >= level.getMaxY()) continue;
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > outerSq || d2 <= carvedSq) continue;
                    pending.add(new BlockPos(cx + dx, y, cz + dz));
                }
            }
        }
        carvedSq = outerSq;
    }

    private void removeSome() {
        int done = 0;
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        while (!pending.isEmpty() && done < MAX_REMOVALS_PER_TICK) {
            BlockPos pos = pending.remove(pending.size() - 1);
            done++;
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0) continue;
            if (protectContainers && state.hasBlockEntity()) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
        }
    }
}
