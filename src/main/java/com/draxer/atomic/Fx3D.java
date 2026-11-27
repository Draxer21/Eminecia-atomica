package com.draxer.atomic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Efectos en 3D hechos con entidades de visualización de ítems (item_display): cubos y cristales
 * que vuelan, giran, crecen y se achican con movimiento suave. Se crean y se animan con comandos
 * (formato de datos estable), duran pocos segundos y se borran solos. Si el servidor se cerró a
 * mitad de un efecto, los restos (etiqueta drax_fx) se eliminan al volver a cargarse.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Fx3D {
    private Fx3D() {}

    public static final String TAG = "drax_fx";
    /** Tope de piezas vivas a la vez, para cuidar el rendimiento. */
    private static final int MAX_ALIVE = 260;

    private record Piece(ServerLevel level, UUID id, int[] age, int moveAt, String finalState, int duration, int killAt) {}

    private static final List<Piece> PIECES = new ArrayList<>();
    private static final Set<UUID> ALIVE = new HashSet<>();
    /** Núcleo de energía de cada jugador que está cargando la espada. */
    private static final Map<UUID, UUID> CORES = new HashMap<>();

    // ---------------------------------------------------------------- piezas

    /** Una transformación: traslación, giro (eje y ángulo) y escala. */
    record Pose(Vec3 offset, Vec3 axis, float angle, Vec3 scale) {
        String nbt() {
            Vec3 a = axis.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : axis.normalize();
            return String.format(Locale.ROOT,
                    "{translation:[%.3ff,%.3ff,%.3ff],left_rotation:{angle:%.4ff,axis:[%.4ff,%.4ff,%.4ff]},"
                            + "right_rotation:[0f,0f,0f,1f],scale:[%.3ff,%.3ff,%.3ff]}",
                    offset.x, offset.y, offset.z, angle, a.x, a.y, a.z, scale.x, scale.y, scale.z);
        }
    }

    static Pose pose(Vec3 offset, Vec3 axis, float angle, double sx, double sy, double sz) {
        return new Pose(offset, axis, angle, new Vec3(sx, sy, sz));
    }

    /**
     * Crea una pieza en {@code at} con el ítem {@code item} (por ejemplo "minecraft:amethyst_block"),
     * en la pose {@code start}; tras {@code moveAt} ticks se anima hasta {@code end} durante
     * {@code duration} ticks y desaparece al terminar.
     */
    static UUID piece(ServerLevel level, Vec3 at, String item, Pose start, int moveAt, Pose end, int duration) {
        if (ALIVE.size() >= MAX_ALIVE) return null;
        UUID id = UUID.randomUUID();
        long most = id.getMostSignificantBits();
        long least = id.getLeastSignificantBits();
        String uuid = String.format(Locale.ROOT, "[I;%d,%d,%d,%d]", (int) (most >> 32), (int) most, (int) (least >> 32), (int) least);
        ALIVE.add(id);
        run(level, String.format(Locale.ROOT,
                "summon minecraft:item_display %.3f %.3f %.3f {UUID:%s,Tags:[\"%s\"],item:{id:\"%s\",count:1},item_display:\"fixed\","
                        + "brightness:{sky:15,block:15},view_range:2.0f,transformation:%s}",
                at.x, at.y, at.z, uuid, TAG, item, start.nbt()));
        PIECES.add(new Piece(level, id, new int[] {0}, Math.max(1, moveAt), end.nbt(), duration, Math.max(1, moveAt) + duration + 1));
        return id;
    }

    private static void run(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
    }

    static void remove(ServerLevel level, UUID id) {
        if (id == null) return;
        Entity e = level.getEntity(id);
        if (e != null) e.discard();
        ALIVE.remove(id);
        PIECES.removeIf(p -> p.id().equals(id));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PIECES.isEmpty()) return;
        Iterator<Piece> it = PIECES.iterator();
        List<Piece> moves = new ArrayList<>();
        while (it.hasNext()) {
            Piece p = it.next();
            int age = ++p.age()[0];
            if (age == p.moveAt()) moves.add(p);
            if (age >= p.killAt()) {
                Entity e = p.level().getEntity(p.id());
                if (e != null) e.discard();
                ALIVE.remove(p.id());
                it.remove();
            }
        }
        for (Piece p : moves) {
            run(p.level(), String.format(Locale.ROOT, "data merge entity %s {start_interpolation:0,interpolation_duration:%d,transformation:%s}",
                    p.id(), p.duration(), p.finalState()));
        }
    }

    /** Restos de un efecto que quedó a medias (por ejemplo, al cerrar el servidor): se borran. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (!event.getLevel().isClientSide() && e.getTags().contains(TAG) && !ALIVE.contains(e.getUUID())) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- efectos

    private static Vec3 randomDir(RandomSource r) {
        Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6 + 0.3, r.nextGaussian());
        return v.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : v.normalize();
    }

    private static final String[] VIOLET = {"minecraft:amethyst_block", "minecraft:purple_stained_glass", "minecraft:magenta_stained_glass",
            "minecraft:crying_obsidian"};
    private static final String[] PINK = {"minecraft:pink_stained_glass", "minecraft:magenta_stained_glass", "minecraft:white_stained_glass"};

    /** Fragmentos que salen volando y girando desde un punto. */
    static void fragments(ServerLevel level, Vec3 center, int count, double reach, String[] items, double size, int life) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < count; i++) {
            Vec3 dir = randomDir(r);
            double s = size * (0.5 + r.nextDouble());
            Vec3 axis = randomDir(r);
            piece(level, center, items[r.nextInt(items.length)],
                    pose(Vec3.ZERO, axis, 0, s, s, s), 1,
                    pose(dir.scale(reach * (0.5 + r.nextDouble() * 0.5)), axis, (float) (Math.PI * (0.6 + r.nextDouble() * 0.35)), s * 0.08, s * 0.08, s * 0.08),
                    life);
        }
    }

    /** Anillo plano de cristal que se expande sobre el suelo. */
    static void ring(ServerLevel level, Vec3 center, double radius, int segments, int life) {
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            // Cada segmento es una losa larga orientada de forma tangente al anillo.
            float yaw = (float) (-a);
            double len = 2 * Math.PI * 1.2 / segments * 1.6;
            double lenEnd = 2 * Math.PI * radius / segments * 1.15;
            piece(level, center, "minecraft:purple_stained_glass",
                    pose(out.scale(1.2), new Vec3(0, 1, 0), yaw, 0.35, 0.35, len), 1,
                    pose(out.scale(radius), new Vec3(0, 1, 0), yaw, 0.6, 0.05, lenEnd),
                    life);
        }
    }

    /** Explosión del Atomic: fragmentos que giran y anillos de onda (más grandes con el radio). */
    static void atomicBurst(ServerLevel level, Vec3 center, int radius) {
        int count = Math.min(28, 10 + radius / 3);
        fragments(level, center, count, Math.min(40, radius * 0.7), VIOLET, 0.9, 30);
        Vec3 ground = center.add(0, -0.8, 0);
        ring(level, ground, Math.min(60, radius * 0.9), 24, 22);
        if (radius >= 40) ring(level, center.add(0, 2.0, 0), Math.min(45, radius * 0.6), 20, 28);
    }

    /** Núcleo de energía: tres cubos girados que crecen (estrella 3D) sobre el lanzador. */
    static void core(ServerLevel level, Vec3 at, int growTicks, double finalSize, UUID owner) {
        Vec3[] axes = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        String[] items = {"minecraft:amethyst_block", "minecraft:purple_stained_glass", "minecraft:white_stained_glass"};
        for (int k = 0; k < 3; k++) {
            UUID id = piece(level, at, items[k], pose(Vec3.ZERO, axes[k], (float) (Math.PI / 4), 0.1, 0.1, 0.1), 1,
                    pose(Vec3.ZERO, axes[(k + 1) % 3], (float) (Math.PI * 0.75), finalSize * (k == 2 ? 0.6 : 1.0),
                            finalSize * (k == 2 ? 0.6 : 1.0), finalSize * (k == 2 ? 0.6 : 1.0)),
                    growTicks);
            if (owner != null && id != null && k == 0) CORES.put(owner, id);
            if (owner != null && id != null) CORE_PARTS.computeIfAbsent(owner, x -> new ArrayList<>()).add(id);
        }
    }

    private static final Map<UUID, List<UUID>> CORE_PARTS = new HashMap<>();

    /** Al soltar o cancelar la carga: el núcleo desaparece. */
    static void clearCore(ServerLevel level, UUID owner) {
        CORES.remove(owner);
        List<UUID> parts = CORE_PARTS.remove(owner);
        if (parts != null) for (UUID id : parts) remove(level, id);
    }

    /** Cristales rotos que flotan alrededor del cuerpo (sobrecompresión, disipación). */
    static void shards(ServerLevel level, Vec3 at, int count, double reach, int life) {
        fragments(level, at, count, reach, PINK, 0.35, life);
    }

    /** Lanza 3D: una vara roja alargada que vuela girando de {@code from} a {@code to}. */
    static void lance(ServerLevel level, Vec3 from, Vec3 to, String item) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5) return;
        Vec3 dir = d.scale(1.0 / len);
        // Giro que lleva el eje Z del modelo hacia la dirección del disparo.
        Vec3 z = new Vec3(0, 0, 1);
        Vec3 axis = z.cross(dir);
        float angle = (float) Math.acos(Math.max(-1, Math.min(1, z.dot(dir))));
        if (axis.lengthSqr() < 1.0E-6) axis = new Vec3(0, 1, 0);
        int life = (int) Math.max(4, Math.min(14, len / 2.5));
        piece(level, from, item, pose(Vec3.ZERO, axis, angle, 0.18, 0.18, 1.8), 1,
                pose(d, axis, angle, 0.12, 0.12, 1.4), life);
    }
}
