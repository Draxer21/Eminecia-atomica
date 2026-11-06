package com.draxer.atomic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Vida en las capitales: de vez en cuando el Culto de Diablos ataca Midgar u Oriana mientras hay
 * jugadores dentro. Los guardias salen a pelear y, si se rechaza el ataque, la ciudad recompensa a
 * quienes estaban ahí. También define la patrulla de los guardias.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class CityEvents {
    private CityEvents() {}

    private record Raid(ServerLevel level, CityLayout.Kind kind, Cities.Site site, List<UUID> attackers, int[] timeout) {}

    private static final List<Raid> RAIDS = new ArrayList<>();
    private static long nextRoll;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (!RAIDS.isEmpty() && level.getGameTime() % 20 == 0) raidTick();
        if (!AtomicConfig.cityRaids() || level.getGameTime() < nextRoll) return;
        nextRoll = level.getGameTime() + 6000; // cada 5 minutos se tira el dado
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextFloat() > AtomicConfig.raidChance()) continue;
            for (CityLayout.Kind kind : new CityLayout.Kind[] {CityLayout.Kind.MIDGAR, CityLayout.Kind.ORIANA}) {
                var site = Cities.siteAt(level, kind, player.getX(), player.getZ());
                if (site.isEmpty() || RAIDS.stream().anyMatch(r -> r.site().key().equals(site.get().key()))) continue;
                startRaid(level, kind, site.get(), player.position(), random);
                return;
            }
        }
    }

    /** Lanza un ataque del Culto en la ciudad, cerca de esa posición. */
    static int startRaid(ServerLevel level, CityLayout.Kind kind, Cities.Site site, Vec3 near, RandomSource random) {
        List<UUID> attackers = new ArrayList<>();
        int groups = 2 + random.nextInt(2);
        for (int g = 0; g < groups; g++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int cx = (int) (near.x + Math.cos(angle) * (18 + random.nextInt(10)));
            int cz = (int) (near.z + Math.sin(angle) * (18 + random.nextInt(10)));
            for (int i = 0; i < 3; i++) {
                int x = cx + random.nextInt(5) - 2;
                int z = cz + random.nextInt(5) - 2;
                BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (!level.isLoaded(ground) || !level.getFluidState(ground).isEmpty()) continue;
                CultistEntity cultist = Cult.CULTIST.value().create(level, EntitySpawnReason.EVENT);
                if (cultist == null) continue;
                cultist.snapTo(x + 0.5, ground.getY(), z + 0.5, random.nextFloat() * 360, 0);
                cultist.setup(i == 0 ? CultistEntity.Kind.MAGE : CultistEntity.Kind.KNIGHT);
                cultist.setPersistenceRequired();
                if (level.addFreshEntity(cultist)) attackers.add(cultist.getUUID());
            }
        }
        if (attackers.isEmpty()) return 0;
        RAIDS.add(new Raid(level, kind, site, attackers, new int[] {300}));
        Component city = Component.translatable("city.draxatomic." + kind.style);
        for (ServerPlayer p : level.players()) {
            if (Cities.siteAt(level, kind, p.getX(), p.getZ()).isPresent()) {
                p.sendSystemMessage(Component.translatable("message.draxatomic.raid_start", city).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.0F, 0.7F);
            }
        }
        return attackers.size();
    }

    private static void raidTick() {
        Iterator<Raid> it = RAIDS.iterator();
        while (it.hasNext()) {
            Raid raid = it.next();
            ServerLevel level = raid.level();
            raid.attackers().removeIf(id -> {
                Entity e = level.getEntity(id);
                return e == null || !e.isAlive();
            });
            if (--raid.timeout()[0] <= 0) {
                it.remove();
                continue;
            }
            if (!raid.attackers().isEmpty()) continue;
            it.remove();
            Component city = Component.translatable("city.draxatomic." + raid.kind().style);
            for (ServerPlayer p : level.players()) {
                if (Cities.siteAt(level, raid.kind(), p.getX(), p.getZ()).isEmpty()) continue;
                p.sendSystemMessage(Component.translatable("message.draxatomic.raid_won", city).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                ItemStack reward = new ItemStack(Items.EMERALD, 12 + level.getRandom().nextInt(9));
                if (!p.getInventory().add(reward)) p.drop(reward, false);
                Magic.addPoints(p, 1);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        }
    }

    /** Guardias: cada tanto caminan hacia otro punto de la ciudad, como en una ronda. */
    public static final class PatrolGoal extends Goal {
        private final PathfinderMob mob;
        private int cooldown;

        public PatrolGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || --cooldown > 0) return false;
            cooldown = 200 + mob.getRandom().nextInt(200);
            return true;
        }

        @Override
        public void start() {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double dist = 14 + mob.getRandom().nextDouble() * 14;
            mob.getNavigation().moveTo(mob.getX() + Math.cos(angle) * dist, mob.getY(), mob.getZ() + Math.sin(angle) * dist, 0.7);
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() == null && !mob.getNavigation().isDone();
        }
    }
}
