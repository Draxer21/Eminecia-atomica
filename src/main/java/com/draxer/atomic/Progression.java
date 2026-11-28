package com.draxer.atomic;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Más formas de ganar experiencia mágica y puntos de habilidad:
 * <ul>
 *   <li>Derrotar enemigos (más por los fuertes; los jefes dan puntos directos).</li>
 *   <li>Golpear a muchos enemigos con una técnica de área.</li>
 *   <li>Logros del mod (1, 2 o 3 puntos según el tipo).</li>
 *   <li>Primera visita a cada ciudad.</li>
 *   <li>El encargo del día.</li>
 *   <li>Cristales de experiencia mágica.</li>
 *   <li>Meditar: agachado, quieto y sin enemigos cerca (mejor sobre amatista y en el Santuario).</li>
 * </ul>
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Progression {
    private Progression() {}

    /** Cristal de experiencia mágica: +250 de experiencia al usarlo. */
    public static final DeferredHolder<Item, Item> CRYSTAL = DraxAtomic.ITEMS.registerItem("magic_crystal",
            properties -> new CrystalItem(properties.stacksTo(64).rarity(Rarity.UNCOMMON)));

    /** Ciudades ya visitadas (bits de {@link ZonePayload}). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> VISITED = Mana.ATTACHMENTS.register("visited_cities",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("value")).copyOnDeath().build());

    /** Encargo del día: [día, tipo, progreso, hecho]. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<List<Integer>>> DAILY = Mana.ATTACHMENTS.register("daily_task",
            () -> AttachmentType.<List<Integer>>builder(() -> List.of(-1, 0, 0, 0))
                    .serialize(Codec.INT.listOf().fieldOf("value")).copyOnDeath().build());

    static final int CRYSTAL_XP = 250;

    // ---------------------------------------------------------------- derrotar enemigos

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player || !(dead.level() instanceof ServerLevel level)) return;
        // Logros de jefes (se dan desde aquí: el formato de predicados de entidad cambió en 26.x).
        if (dead instanceof RoundsKnightEntity knight) {
            Achievements.grant(player, "round_knight");
            if (knight.rank() == RoundsKnightEntity.Rank.VOID) Achievements.grant(player, "void_knight");
        }
        if (dead instanceof FoeEntity foe && foe.foe() == FoeEntity.Foe.CHILD_1) Achievements.grant(player, "first_child");
        if (dead instanceof BeastEntity beast) {
            if (beast.beast() == BeastEntity.Beast.CHIMERA_ALPHA) Achievements.grant(player, "chimera_alpha");
            if (beast.beast() == BeastEntity.Beast.MANA_WRAITH) Achievements.grant(player, "mana_wraith");
            if (beast.beast() == BeastEntity.Beast.SHADOW_BEAST) Achievements.grant(player, "shadow_beast");
        }
        if (dead instanceof BloodLordEntity lord) Achievements.grant(player, lord.lord() == BloodLordEntity.Lord.CRIMSON ? "crimson" : "queen");
        if (dead instanceof AuroraEntity) Achievements.grant(player, "aurora");
        int points = bossPoints(dead);
        if (points > 0) {
            Magic.addPoints(player, points);
            player.sendSystemMessage(Component.translatable("message.draxatomic.boss_points", points, dead.getDisplayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            give(player, new ItemStack(CRYSTAL.value(), 2 + level.getRandom().nextInt(3)));
        } else if (dead instanceof Enemy) {
            // Experiencia según lo resistente que era: 5 a 60.
            int xp = Math.max(5, Math.min(60, Math.round(dead.getMaxHealth() * 1.2F)));
            if (dead instanceof CultistEntity || dead instanceof VampireEntity || dead instanceof DuelistEntity || dead instanceof FoeEntity) xp += 20;
            if (dead instanceof BeastEntity b && (b.beast() == BeastEntity.Beast.MANA_WRAITH || b.beast() == BeastEntity.Beast.SHADOW_BEAST)) xp += 30;
            Magic.addXp(player, xp);
            float chance = dead instanceof CultistEntity || dead instanceof FoeEntity ? 0.08F
                    : dead instanceof VampireEntity || dead instanceof BeastEntity ? 0.05F : 0.01F;
            if (level.getRandom().nextFloat() < chance) dead.spawnAtLocation(level, new ItemStack(CRYSTAL.value()));
        }
        if (dead instanceof Enemy) {
            dailyProgress(player, Daily.KILL, 1);
            if (dead instanceof CultistEntity || dead instanceof VampireEntity || dead instanceof FoeEntity || dead instanceof BeastEntity) {
                dailyProgress(player, Daily.HUNT, 1);
            }
        }
    }

    private static int bossPoints(LivingEntity e) {
        if (e instanceof BloodLordEntity lord) return lord.lord() == BloodLordEntity.Lord.ELIZABETH ? 5 : 3;
        if (e instanceof AuroraEntity) return 5;
        if (e instanceof RoundsKnightEntity k) return k.rank() == RoundsKnightEntity.Rank.VOID ? 6 : 3;
        if (e instanceof BeastEntity b && b.beast() == BeastEntity.Beast.CHIMERA_ALPHA) return 4;
        return 0;
    }

    /** Cada enemigo alcanzado por una técnica de área (Atomic, All-Range…) da algo de experiencia. */
    static void techniqueHit(Entity caster, Entity victim) {
        if (caster instanceof ServerPlayer player && victim instanceof Enemy) Magic.addXp(player, 5);
    }

    // ---------------------------------------------------------------- logros

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var holder = event.getAdvancement();
        if (!DraxAtomic.MOD_ID.equals(holder.id().getNamespace())) return;
        int points = holder.value().display().map(d -> switch (d.getType()) {
            case CHALLENGE -> 3;
            case GOAL -> 2;
            default -> 1;
        }).orElse(0);
        if (points <= 0) return;
        Magic.addPoints(player, points);
        player.sendSystemMessage(Component.translatable("message.draxatomic.advancement_points", points).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ---------------------------------------------------------------- primera visita

    /** Lo llama Zones cada vez que recalcula la zona del jugador. */
    static void visit(ServerPlayer player, int zones) {
        int visited = player.getData(VISITED);
        int fresh = zones & ~visited & (ZonePayload.MIDGAR | ZonePayload.ORIANA | ZonePayload.SANCTUARY | ZonePayload.LAWLESS);
        if (fresh == 0) return;
        player.setData(VISITED, visited | fresh);
        int points = 2 * Integer.bitCount(fresh);
        Magic.addPoints(player, points);
        player.sendSystemMessage(Component.translatable("message.draxatomic.first_visit", points).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ---------------------------------------------------------------- encargo del día

    public enum Daily {
        KILL("kill", 15),
        SPEND("spend", 1200),
        CAST("cast", 12),
        HUNT("hunt", 4),
        MEDITATE("meditate", 60);

        public final String id;
        public final int goal;

        Daily(String id, int goal) {
            this.id = id;
            this.goal = goal;
        }
    }

    private static long today(ServerPlayer player) {
        return ((ServerLevel) player.level()).getServer().overworld().getGameTime() / 24000L;
    }

    /** Devuelve el encargo de hoy, sorteando uno nuevo si cambió el día. */
    static List<Integer> daily(ServerPlayer player) {
        List<Integer> d = player.getData(DAILY);
        long day = today(player);
        if (d.size() < 4 || d.get(0) != (int) day) {
            int type = Math.floorMod((int) (day * 31 + player.getUUID().hashCode()), Daily.values().length);
            d = List.of((int) day, type, 0, 0);
            player.setData(DAILY, d);
            Daily task = Daily.values()[type];
            player.sendSystemMessage(Component.translatable("message.draxatomic.daily_new",
                    Component.translatable("daily.draxatomic." + task.id, task.goal)).withStyle(ChatFormatting.AQUA));
        }
        return d;
    }

    static void dailyProgress(ServerPlayer player, Daily kind, int amount) {
        List<Integer> d = daily(player);
        if (d.get(3) != 0 || d.get(1) != kind.ordinal()) return;
        int progress = Math.min(kind.goal, d.get(2) + amount);
        boolean done = progress >= kind.goal;
        player.setData(DAILY, List.of(d.get(0), d.get(1), progress, done ? 1 : 0));
        if (done) {
            Magic.addPoints(player, 2);
            give(player, new ItemStack(Items.EMERALD, 8));
            give(player, new ItemStack(CRYSTAL.value()));
            player.sendSystemMessage(Component.translatable("message.draxatomic.daily_done").withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        } else if (kind != Daily.SPEND || progress % 200 < amount) {
            player.sendOverlayMessage(Component.translatable("message.draxatomic.daily_progress",
                    Component.translatable("daily.draxatomic." + kind.id, kind.goal), progress, kind.goal).withStyle(ChatFormatting.AQUA));
        }
    }

    public static Component dailyLine(ServerPlayer player) {
        List<Integer> d = daily(player);
        Daily task = Daily.values()[Math.floorMod(d.get(1), Daily.values().length)];
        return Component.translatable(d.get(3) != 0 ? "message.draxatomic.daily_status_done" : "message.draxatomic.daily_status",
                Component.translatable("daily.draxatomic." + task.id, task.goal), d.get(2), task.goal);
    }

    // ---------------------------------------------------------------- meditar

    private static final Map<UUID, Integer> STILL = new HashMap<>();
    private static final DustParticleOptions CALM = new DustParticleOptions(0xB07CFF, 0.7F);

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % 200 == 13) daily(player);
        boolean still = player.isShiftKeyDown() && player.onGround() && !player.isUsingItem()
                && player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
        if (!still) {
            STILL.remove(player.getUUID());
            return;
        }
        int t = STILL.merge(player.getUUID(), 1, Integer::sum);
        if (t < 60 || t % 20 != 0) return;
        if (!level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(10), e -> e instanceof Enemy).isEmpty()) return;
        BlockPos below = player.blockPosition().below();
        int xp = 2;
        if (level.getBlockState(below).is(Blocks.AMETHYST_BLOCK)) xp *= 3;
        if (Cities.inSanctuary(level, player)) xp *= 2;
        Magic.addXp(player, xp);
        dailyProgress(player, Daily.MEDITATE, 1);
        for (int i = 0; i < 6; i++) {
            double a = (t / 20.0 + i) * Math.PI / 3;
            level.sendParticles(CALM, player.getX() + Math.cos(a) * 0.9, player.getY() + 0.3 + i * 0.15, player.getZ() + Math.sin(a) * 0.9,
                    1, 0, 0, 0, 0);
        }
        if (t == 60) player.sendOverlayMessage(Component.translatable("message.draxatomic.meditating").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ---------------------------------------------------------------- ítem

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public static final class CrystalItem extends Item {
        public CrystalItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp) {
                ItemStack stack = player.getItemInHand(hand);
                int count = sp.isShiftKeyDown() ? stack.getCount() : 1;
                Magic.addXp(sp, CRYSTAL_XP * count);
                stack.consume(count, player);
                level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.4F);
                ((ServerLevel) level).sendParticles(CALM, sp.getX(), sp.getY() + 1, sp.getZ(), 20 + 5 * Math.min(count, 20), 0.4, 0.6, 0.4, 0.05);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic.magic_crystal", CRYSTAL_XP).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
