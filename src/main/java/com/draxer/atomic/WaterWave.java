package com.draxer.atomic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * La ola del All-Range Atomic: el agua que había dentro de la esfera (lagos, fosos, el mar) sale
 * empujada hacia fuera y cae como una capa de agua alrededor del cráter, en la misma dirección
 * en la que estaba. El agua que se coloca fluye sola y termina de inundar los alrededores.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class WaterWave {
    private WaterWave() {}

    /** Sectores angulares en los que se reparte el agua. */
    static final int SECTORS = 64;
    /** Sectores que se vuelcan por tick (la ola recorre el anillo en unos pocos ticks). */
    private static final int SECTORS_PER_TICK = 6;

    private record Wave(ServerLevel level, Vec3 center, int radius, long[] volume, int[] state) {}

    private static final List<Wave> WAVES = new ArrayList<>();

    /** Cuenta, por sector, los bloques de agua que hay dentro de la esfera (antes de la explosión). */
    static long[] measure(ServerLevel level, Vec3 center, int radius) {
        long[] volume = new long[SECTORS];
        int cx = Mth.floor(center.x);
        int cy = Mth.floor(center.y);
        int cz = Mth.floor(center.z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long total = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > radius * radius) continue;
                int x = cx + dx;
                int z = cz + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                if (top <= floor) continue;
                double h = Math.sqrt(radius * radius - d2);
                int hi = Math.min(top - 1, (int) (cy + h));
                int lo = Math.max(floor, (int) (cy - h));
                if (hi < lo) continue;
                if (!level.getFluidState(pos.set(x, top - 1, z)).is(FluidTags.WATER)) continue;
                int sector = Math.floorMod((int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI * 2) * SECTORS), SECTORS);
                volume[sector] += hi - lo + 1;
                total += hi - lo + 1;
            }
        }
        return total > 0 ? volume : null;
    }

    static void start(ServerLevel level, Vec3 center, int radius, long[] volume, int delay) {
        if (volume == null) return;
        long total = 0;
        for (long v : volume) total += v;
        long max = AtomicConfig.allRangeWaveMax();
        if (total > max) {
            for (int i = 0; i < SECTORS; i++) volume[i] = volume[i] * max / total;
        }
        com.mojang.logging.LogUtils.getLogger().info("Drax Atomic: ola del All-Range con {} bloques de agua", Math.min(total, max));
        WAVES.add(new Wave(level, center, radius, volume, new int[] {delay, 0}));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (WAVES.isEmpty()) return;
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            if (w.state()[0] > 0) {
                w.state()[0]--;
                continue;
            }
            int sector = w.state()[1];
            if (sector == 0) announce(w);
            for (int k = 0; k < SECTORS_PER_TICK && sector < SECTORS; k++, sector++) pour(w, sector);
            w.state()[1] = sector;
            if (sector >= SECTORS) it.remove();
        }
    }

    private static void announce(Wave w) {
        ServerLevel level = w.level();
        Vec3 c = w.center();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 4.0F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.BLOCKS, 4.0F, 0.6F);
    }

    /** Vuelca el agua de un sector en un arco cada vez más lejano, una capa sobre el suelo. */
    private static void pour(Wave w, int sector) {
        long budget = w.volume()[sector];
        if (budget <= 0) return;
        ServerLevel level = w.level();
        int cx = Mth.floor(w.center().x);
        int cz = Mth.floor(w.center().z);
        double a0 = sector * Math.PI * 2 / SECTORS - Math.PI;
        double a1 = a0 + Math.PI * 2 / SECTORS;
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int limit = w.radius() * 2 + 48;
        boolean splashed = false;
        for (int d = w.radius() + 2; d < limit && budget > 0; d++) {
            for (double a = a0; a < a1 && budget > 0; a += 0.8 / d) {
                int x = cx + (int) Math.round(Math.cos(a) * d);
                int z = cz + (int) Math.round(Math.sin(a) * d);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (top <= level.getMinY() || top >= level.getMaxY()) continue;
                if (level.getFluidState(pos.set(x, top - 1, z)).is(FluidTags.WATER)) continue;
                pos.set(x, top, z);
                BlockState here = level.getBlockState(pos);
                if (!here.isAir() && !here.canBeReplaced()) continue;
                level.setBlock(pos, water, Block.UPDATE_ALL);
                budget--;
                if (!splashed || budget % 40 == 0) {
                    level.sendParticles(ParticleTypes.SPLASH, true, false, x + 0.5, top + 1.0, z + 0.5, 12, 0.6, 0.6, 0.6, 0.2);
                    level.sendParticles(ParticleTypes.CLOUD, true, false, x + 0.5, top + 1.5, z + 0.5, 2, 0.8, 1.0, 0.8, 0.05);
                    splashed = true;
                }
            }
        }
    }
}
