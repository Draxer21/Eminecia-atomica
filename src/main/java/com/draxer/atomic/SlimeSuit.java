package com.draxer.atomic;

import java.util.Map;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * El traje de slime: capucha, abrigo, pantalones y botas de slime negro.
 * <ul>
 *   <li>Cada pieza: +25 de poder mágico máximo y +1 de recarga por segundo.</li>
 *   <li>Capucha: visión nocturna.</li>
 *   <li>Abrigo: 35 % de probabilidad de desviar proyectiles.</li>
 *   <li>Pantalones: velocidad.</li>
 *   <li>Botas: sin daño por caída y doble salto en el aire (5 de poder).</li>
 *   <li>Set completo: recarga doble y, agachado, los monstruos a más de 8 bloques pierden tu rastro.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class SlimeSuit {
    private SlimeSuit() {}

    static void init() {}

    public static final int MANA_PER_PIECE = 25;
    public static final int DOUBLE_JUMP_COST = 5;

    private static final ResourceKey<EquipmentAsset> ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "slime_suit"));
    private static final TagKey<Item> REPAIR =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "repairs_slime_suit"));

    public static final ArmorMaterial MATERIAL = new ArmorMaterial(37,
            Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 8),
            18, SoundEvents.ARMOR_EQUIP_LEATHER, 3.0F, 0.1F, REPAIR, ASSET);

    public static final DeferredHolder<Item, Item> HOOD = piece("slime_hood", ArmorType.HELMET);
    public static final DeferredHolder<Item, Item> COAT = piece("slime_coat", ArmorType.CHESTPLATE);
    public static final DeferredHolder<Item, Item> PANTS = piece("slime_pants", ArmorType.LEGGINGS);
    public static final DeferredHolder<Item, Item> BOOTS = piece("slime_boots", ArmorType.BOOTS);

    private static DeferredHolder<Item, Item> piece(String id, ArmorType type) {
        return DraxAtomic.ITEMS.registerItem(id, properties -> new SuitPiece(properties.humanoidArmor(MATERIAL, type)
                .fireResistant().rarity(Rarity.EPIC), id));
    }

    /** Pieza del traje: solo agrega su descripción al tooltip. */
    public static final class SuitPiece extends Item {
        private final String id;

        public SuitPiece(Properties properties, String id) {
            super(properties);
            this.id = id;
        }

        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack stack, Item.TooltipContext context,
                                    net.minecraft.world.item.component.TooltipDisplay display,
                                    java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip,
                                    net.minecraft.world.item.TooltipFlag flag) {
            tooltip.accept(net.minecraft.network.chat.Component.translatable("tooltip.draxatomic." + id)
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
            tooltip.accept(net.minecraft.network.chat.Component.translatable("tooltip.draxatomic.slime_suit.piece", MANA_PER_PIECE)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.accept(net.minecraft.network.chat.Component.translatable("tooltip.draxatomic.slime_suit.set")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean wearing(Player player, EquipmentSlot slot, DeferredHolder<Item, Item> item) {
        return player.getItemBySlot(slot).is(item.value());
    }

    /** Piezas del traje puestas (0-4). */
    public static int pieces(Player player) {
        int n = 0;
        if (wearing(player, EquipmentSlot.HEAD, HOOD)) n++;
        if (wearing(player, EquipmentSlot.CHEST, COAT)) n++;
        if (wearing(player, EquipmentSlot.LEGS, PANTS)) n++;
        if (wearing(player, EquipmentSlot.FEET, BOOTS)) n++;
        return n;
    }

    // ---------------------------------------------------------------- efectos

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (wearing(player, EquipmentSlot.HEAD, HOOD)) effect(player, MobEffects.NIGHT_VISION, 0, 260);
        if (wearing(player, EquipmentSlot.LEGS, PANTS)) effect(player, MobEffects.SPEED, 0, 40);
        if (pieces(player) == 4 && player.isShiftKeyDown()) hide(player);
    }

    private static void effect(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type,
                               int amplifier, int duration) {
        player.addEffect(new MobEffectInstance(type, duration, amplifier, true, false, true));
    }

    /** Los monstruos lejanos pierden el rastro de quien se esconde en la sombra. */
    private static void hide(ServerPlayer player) {
        double far = 8.0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32))) {
            if (mob.getTarget() == player && mob.distanceToSqr(player) > far * far) mob.setTarget(null);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getSource().is(DamageTypeTags.IS_FALL) && wearing(player, EquipmentSlot.FEET, BOOTS)) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE) && wearing(player, EquipmentSlot.CHEST, COAT)
                && player.getRandom().nextFloat() < 0.35F) {
            event.setCanceled(true);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(new DustParticleOptions(0x8A3BFF, 1.2F), true, false,
                        player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.4, 0.5, 0.4, 0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_HIT,
                        SoundSource.PLAYERS, 1.0F, 0.8F);
            }
        }
    }

    /** Doble salto, pedido por el cliente (él ya aplicó el impulso). */
    static void doubleJump(ServerPlayer player) {
        if (!wearing(player, EquipmentSlot.FEET, BOOTS) || player.onGround()) return;
        if (!Mana.spend(player, DOUBLE_JUMP_COST)) return;
        player.fallDistance = 0;
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(new DustParticleOptions(0x2A1048, 1.6F), true, false,
                player.getX(), player.getY(), player.getZ(), 16, 0.4, 0.05, 0.4, 0);
        level.sendParticles(new DustParticleOptions(0x8A3BFF, 1.0F), true, false,
                player.getX(), player.getY(), player.getZ(), 8, 0.5, 0.05, 0.5, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP,
                SoundSource.PLAYERS, 1.0F, 0.7F);
    }
}
