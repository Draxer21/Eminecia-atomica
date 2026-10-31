package com.draxer.atomic;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * La espada de slime. Funciona como espada normal; manteniendo clic derecho reúne poder
 * mágico (círculo violeta, levitación, la frase) y al completar la carga libera la
 * explosión atómica. Soltar antes de tiempo cancela el ataque.
 */
public final class SlimeSwordItem extends Item {
    private static final int VIOLET = 0x8A3BFF;
    private static final int CYAN = 0x5FE6FF;

    public SlimeSwordItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- uso

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Shift + clic derecho: cambiar de modo.
        if (player.isSecondaryUseActive()) {
            SwordMode next = SwordMode.of(stack).next();
            SwordMode.set(stack, next);
            if (!level.isClientSide()) {
                player.sendOverlayMessage(next == SwordMode.ALL_RANGE_ATOMIC
                        ? Component.translatable("message.draxatomic.mode_compressed", next.title())
                        : Component.translatable("message.draxatomic.mode", next.title(), next.cost()));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 1.0F, 0.6F + next.ordinal() * 0.2F);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        SwordMode mode = SwordMode.of(stack);
        int cost = Magic.cost(player, mode.cost());
        if (!Mana.has(player, cost)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.draxatomic.no_power", cost)
                        .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (mode == SwordMode.ALL_RANGE_ATOMIC && Magic.compressionLevel(player) <= 0) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.draxatomic.need_compression").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (mode.charged()) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            boolean done = switch (mode) {
                case WHIP -> { SlimeSkills.whip(server, serverPlayer); yield true; }
                case DASH -> { SlimeSkills.dash(server, serverPlayer); yield true; }
                case BLADE_RAIN -> SlimeSkills.bladeRain(server, serverPlayer);
                default -> false;
            };
            if (!done) return InteractionResult.FAIL;
            Mana.spend(serverPlayer, cost);
            player.getCooldowns().addCooldown(stack, switch (mode) {
                case WHIP -> 10;
                case DASH -> 25;
                default -> 50;
            });
            player.swing(hand, true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        int base = SwordMode.of(stack).chargeTicks();
        return entity instanceof Player p ? Math.max(10, Math.round(base * Magic.chargeFactor(p))) : base;
    }

    /** Cada tick de carga: aura, sonidos y la frase por partes. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) return;
        int total = getUseDuration(stack, entity);
        int elapsed = total - remaining;
        float progress = Math.min(1.0F, elapsed / (float) total);

        if (elapsed == 1) {
            // El domo de barrera: marca el área exacta que alcanzará el ataque.
            SwordMode mode = SwordMode.of(stack);
            float comp = Magic.peekCompression(player);
            int radius = switch (mode) {
                case ALL_RANGE_ATOMIC -> AtomicVariants.allRangeRadius(player, Magic.compressionLevel(player));
                case RECOVERY_ATOMIC -> Math.round(AtomicConfig.recoveryRadius() * Magic.reach(player));
                default -> AtomicBlast.atomicRadius(player, comp);
            };
            BarrierPayload.send(server, player, radius, total + 50,
                    mode == SwordMode.RECOVERY_ATOMIC ? AtomicPayload.RECOVERY : AtomicPayload.VIOLET);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                    SoundSource.PLAYERS, 1.5F, 0.6F);
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, total, 0, false, false));
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 1.5F, 0.6F);
        }
        if (elapsed % 20 == 5) {
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.PLAYERS, 1.2F, 0.5F + progress);
        }
        if (elapsed == Math.max(2, total - 25)) {
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.PLAYERS, 2.0F, 0.7F);
        }

        // La frase: "I..." → "I... am..." (la palabra final sale al detonar).
        if (elapsed == Math.round(total * 0.2F)) phrase(player, "message.draxatomic.phrase_1");
        if (elapsed == Math.round(total * 0.55F)) phrase(player, "message.draxatomic.phrase_2");

        aura(server, player, elapsed, progress, SwordMode.of(stack));
        if (SwordMode.of(stack) == SwordMode.ALL_RANGE_ATOMIC && elapsed % 4 == 2) AtomicVariants.lockOn(server, player);
    }

    /** Círculo mágico a los pies que gira y se cierra, más energía que fluye hacia el jugador. */
    private static void aura(ServerLevel level, Player player, int elapsed, float progress, SwordMode mode) {
        double cx = player.getX();
        double cy = player.getY() + 0.1;
        double cz = player.getZ();
        double ring = 3.5 - 1.5 * progress;
        
        double spin = elapsed * 0.25;
        boolean heal = mode == SwordMode.RECOVERY_ATOMIC;
        DustParticleOptions violet = new DustParticleOptions(heal ? AtomicPayload.RECOVERY : VIOLET, 1.4F);
        DustParticleOptions cyan = new DustParticleOptions(heal ? 0xFFF6B0 : CYAN, 1.0F);
        if (mode == SwordMode.ALL_RANGE_ATOMIC) ring += 2.5; // círculo más grande
        int points = 24;
        for (int i = 0; i < points; i++) {
            double a = spin + i * Math.PI * 2 / points;
            level.sendParticles(violet, true, false, cx + Math.cos(a) * ring, cy, cz + Math.sin(a) * ring,
                    1, 0, 0, 0, 0);
            double b = -spin * 1.5 + i * Math.PI * 2 / points;
            level.sendParticles(cyan, true, false, cx + Math.cos(b) * ring * 0.6, cy, cz + Math.sin(b) * ring * 0.6,
                    1, 0, 0, 0, 0);
        }
        // Energía absorbida desde el entorno.
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, false, cx, player.getY() + 1.0, cz,
                6 + Math.round(14 * progress), 2.5, 1.5, 2.5, 0.05);
        if (elapsed % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, true, false, cx, player.getY() + 1.0, cz,
                    3, 0.4, 0.8, 0.4, 0.02);
        }
    }

    /** Carga completa: detona. */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
            SwordMode mode = SwordMode.of(stack);
            int cost = Magic.cost(player, mode.cost());
            if (!Mana.has(player, cost)) {
                releaseUsing(stack, level, entity, 0);
                return stack;
            }
            if (mode == SwordMode.ALL_RANGE_ATOMIC && Magic.compressionLevel(player) <= 0) {
                // La compresión se disipó durante la carga.
                player.sendOverlayMessage(Component.translatable("message.draxatomic.need_compression").withStyle(ChatFormatting.RED));
                releaseUsing(stack, level, entity, 0);
                return stack;
            }
            Mana.spend(player, cost);
            player.getCooldowns().addCooldown(stack, mode == SwordMode.RECOVERY_ATOMIC ? 100 : AtomicConfig.cooldownTicks());
            player.removeEffect(MobEffects.LEVITATION);
            // Flota en lugar de caer.
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0, false, false));
            player.swing(InteractionHand.MAIN_HAND, true);
            finalWord(server, player, mode);
            if (mode == SwordMode.ATOMIC) AtomicBlast.detonate(server, player);
            else if (mode == SwordMode.RECOVERY_ATOMIC) AtomicVariants.recovery(server, player);
            else if (mode == SwordMode.ALL_RANGE_ATOMIC) AtomicVariants.allRange(server, player);
        }
        return stack;
    }

    /** Soltar antes de tiempo: el poder se disipa y queda un breve enfriamiento. */
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
            player.removeEffect(MobEffects.LEVITATION);
            player.getCooldowns().addCooldown(stack, 40);
            BarrierPayload.send(server, player, 0, 0, 0);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
            server.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.6, 0.6, 0.6, 0.02);
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
        }
        return true;
    }

    // ---------------------------------------------------------------- la frase

    private static void phrase(ServerPlayer player, String key) {
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC)));
    }

    /** La palabra final, en grande, para todos los que estén cerca. */
    private static void finalWord(ServerLevel level, ServerPlayer caster, SwordMode mode) {
        double reach = Math.max(AtomicConfig.radius() * 4.0, 320.0);
        ChatFormatting color = mode == SwordMode.RECOVERY_ATOMIC ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE;
        Component title = Component.translatable("message.draxatomic.title." + mode.id())
                .withStyle(color, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.draxatomic.full." + mode.id())
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(caster) > reach * reach) continue;
            player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 40, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
        caster.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
    }

    // ---------------------------------------------------------------- tooltip

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.draxatomic.slime_sword.1").withStyle(ChatFormatting.DARK_PURPLE));
        SwordMode mode = SwordMode.of(stack);
        tooltip.accept(Component.translatable("tooltip.draxatomic.slime_sword.mode", mode.title(), mode.cost()));
        tooltip.accept(Component.translatable("tooltip.draxatomic.slime_sword.help." + mode.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.draxatomic.slime_sword.switch").withStyle(ChatFormatting.DARK_GRAY));
    }
}
