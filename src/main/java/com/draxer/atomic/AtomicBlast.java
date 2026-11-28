package com.draxer.atomic;

import java.util.ArrayDeque;
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
    /** Máximo global de pasos de escaneo y tallado de la cáscara por tick del servidor. */
    private static final int MAX_BLOCK_SCAN_OPERATIONS_PER_TICK = 200_000;
    /** Límite global de bloques modificados por tick, compartido por todas las explosiones. */
    private static final int MAX_REMOVALS_PER_TICK = 80_000;

    private static final List<AtomicBlast> ACTIVE = new ArrayList<>();
    private static int nextBudgetStart;

    private final ServerLevel level;
    private final UUID casterId;
    private final Vec3 center;
    private final int radius;
    private final float damage;
    private final boolean carve;
    private final boolean protectContainers;
    private final Set<UUID> hit = new HashSet<>();
    private final ArrayDeque<ShellScan> shellScans = new ArrayDeque<>();
    private final BlockPos.MutableBlockPos removalPos = new BlockPos.MutableBlockPos();
    private int age;
    /** Radio (al cuadrado) cuya cáscara ya fue programada, aunque siga pendiente de tallar. */
    private double scheduledSq = -1;

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

    /** Radio final del Atomic con mejoras y compresión (tope: 160). */
    public static int atomicRadius(net.minecraft.world.entity.player.Player caster, float comp) {
        return Math.min(160, Math.round(AtomicConfig.radius() * Magic.reach(caster) * (1.0F + (comp - 1.0F) * 0.4F)));
    }

    /** El Atomic normal, centrado en quien lo lanza. */
    public static void detonate(ServerLevel level, ServerPlayer caster) {
        // La compresión agranda la esfera y multiplica el daño; el árbol de habilidades también.
        float comp = Magic.consumeCompression(caster);
        int radius = atomicRadius(caster, comp);
        AtomicBlast blast = new AtomicBlast(level, caster.getUUID(), caster.position().add(0, 1.0, 0),
                radius, AtomicConfig.damage() * comp * Magic.power(caster), AtomicConfig.destroyBlocks(), true);
        ACTIVE.add(blast);
        blast.flash();
        Monsters.afterBigMagic(level, caster.position());
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
        if (big) Monsters.afterBigMagic(level, center);
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
        Fx3D.atomicBurst(level, center, radius);
        // Anillos de onda expansiva propios alrededor del centro.
        level.sendParticles(ModParticles.SHOCKWAVE.value(), true, true, x, y, z, 10, Math.min(6.0, radius * 0.15), 0.6,
                Math.min(6.0, radius * 0.15), 0.0);
    }

    // ---------------------------------------------------------------- ticks

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            nextBudgetStart = 0;
            return;
        }
        Iterator<AtomicBlast> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (!it.next().tick()) it.remove();
        }
        if (ACTIVE.isEmpty()) {
            nextBudgetStart = 0;
            return;
        }

        // Repartir el mismo presupuesto entre las explosiones activas. El punto inicial rota
        // cada tick para que un Atomic grande no deje esperando siempre al All-Range.
        List<AtomicBlast> carving = new ArrayList<>();
        for (AtomicBlast blast : ACTIVE) {
            if (blast.carve && !blast.shellScans.isEmpty()) carving.add(blast);
        }
        if (carving.isEmpty()) {
            nextBudgetStart = 0;
            return;
        }

        int scanRemaining = MAX_BLOCK_SCAN_OPERATIONS_PER_TICK;
        int removalRemaining = MAX_REMOVALS_PER_TICK;
        int start = Math.floorMod(nextBudgetStart, carving.size());
        for (int offset = 0; offset < carving.size() && scanRemaining > 0; offset++) {
            AtomicBlast blast = carving.get((start + offset) % carving.size());
            int blastsLeft = carving.size() - offset;
            int scanShare = scanRemaining / blastsLeft;
            int removalShare = removalRemaining / blastsLeft;
            BlockProgress progress = blast.processShell(scanShare, removalShare);
            scanRemaining -= progress.scans();
            removalRemaining -= progress.removals();
        }
        nextBudgetStart = (start + 1) % carving.size();
    }

    /** Un paso; false cuando terminó. */
    private boolean tick() {
        age++;
        if (age <= EXPAND_TICKS + 1) {
            double current = radius * ease(Math.min(1.0, age / (double) EXPAND_TICKS));
            shockFront(current);
            if (carve) scheduleShell(current);
        }
        return age <= EXPAND_TICKS + 1 || !shellScans.isEmpty();
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
            Progression.techniqueHit(caster, entity);
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

    /** Programa la nueva cáscara; el escaneo y el tallado avanzan con el presupuesto global. */
    private void scheduleShell(double current) {
        double outerSq = current * current;
        if (outerSq <= scheduledSq) return;
        shellScans.addLast(new ShellScan(center, Mth.ceil(current), scheduledSq, outerSq));
        scheduledSq = outerSq;
    }

    /** Procesa posiciones de cáscara sin construir una lista grande de bloques pendientes. */
    private BlockProgress processShell(int scanLimit, int removalLimit) {
        int scans = 0;
        int removals = 0;
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        while (scans < scanLimit && !shellScans.isEmpty()) {
            ShellScan scan = shellScans.peekFirst();
            if (!scan.columnReady) {
                if (scan.advanceColumn()) {
                    shellScans.removeFirst();
                    continue;
                }
                scans++;
                continue;
            }
            if (!scan.hasCurrentBlock()) {
                scan.columnReady = false;
                continue;
            }
            int y = scan.cy + scan.dy;
            if (y < level.getMinY() || y >= level.getMaxY()) {
                scan.advanceBlock();
                scans++;
                continue;
            }
            // No inspeccionar más bloques destructibles cuando se agotó la cuota compartida.
            if (removals >= removalLimit) break;

            removalPos.set(scan.cx + scan.columnX, y, scan.cz + scan.columnZ);
            if (level.isLoaded(removalPos)) {
                BlockState state = level.getBlockState(removalPos);
                if (!state.isAir() && state.getDestroySpeed(level, removalPos) >= 0
                        && !(protectContainers && state.hasBlockEntity())) {
                    level.setBlock(removalPos, Blocks.AIR.defaultBlockState(), flags);
                    removals++;
                }
            }
            scan.advanceBlock();
            scans++;
        }
        return new BlockProgress(scans, removals);
    }

    private record BlockProgress(int scans, int removals) {}

    /** Cursor de una cáscara esférica. Solo conserva coordenadas, no todos sus bloques. */
    private static final class ShellScan {
        private final int radius;
        private final int cx;
        private final int cy;
        private final int cz;
        private final double outerSq;
        private final double innerSq;
        private int nextX;
        private int nextZ;
        private int columnX;
        private int columnZ;
        private int outerY;
        private int innerY;
        private int dy;
        private boolean positiveRange;
        private boolean columnReady;

        private ShellScan(Vec3 center, int radius, double innerSq, double outerSq) {
            this.radius = radius;
            this.cx = Mth.floor(center.x);
            this.cy = Mth.floor(center.y);
            this.cz = Mth.floor(center.z);
            this.innerSq = innerSq;
            this.outerSq = outerSq;
            this.nextX = -radius;
            this.nextZ = -radius;
        }

        /** Avanza una columna; devuelve true cuando ya terminó la cáscara. */
        private boolean advanceColumn() {
            if (nextX > radius) return true;
            columnX = nextX;
            columnZ = nextZ;
            nextZ++;
            if (nextZ > radius) {
                nextZ = -radius;
                nextX++;
            }

            double horizontalSq = columnX * columnX + columnZ * columnZ;
            if (horizontalSq > outerSq) return false;
            outerY = (int) Math.floor(Math.sqrt(outerSq - horizontalSq));
            double columnInnerSq = innerSq - horizontalSq;
            innerY = columnInnerSq < 0 ? -1 : (int) Math.floor(Math.sqrt(columnInnerSq));
            if (outerY <= innerY) return false;

            positiveRange = innerY < 0;
            dy = -outerY;
            columnReady = true;
            return false;
        }

        private boolean hasCurrentBlock() {
            int rangeEnd = positiveRange ? outerY : -innerY - 1;
            return dy <= rangeEnd;
        }

        private void advanceBlock() {
            int rangeEnd = positiveRange ? outerY : -innerY - 1;
            dy++;
            if (dy <= rangeEnd) return;
            if (!positiveRange && innerY >= 0) {
                positiveRange = true;
                dy = innerY + 1;
                return;
            }
            columnReady = false;
        }
    }
}
