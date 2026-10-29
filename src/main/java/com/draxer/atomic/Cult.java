package com.draxer.atomic;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * La Culto de Diablos: cultistas que patrullan de noche, sus fragmentos, el sello que invoca
 * al Caballero de la Mesa Redonda y el corazón que suelta al morir.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Cult {
    private Cult() {}

    private static ResourceKey<EntityType<?>> key(String id) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, id));
    }

    public static final DeferredHolder<EntityType<?>, EntityType<CultistEntity>> CULTIST = ShadowGarden.ENTITY_TYPES.register("diablos_cultist", () ->
            EntityType.Builder.of(CultistEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(key("diablos_cultist")));

    public static final DeferredHolder<EntityType<?>, EntityType<RoundsKnightEntity>> KNIGHT = ShadowGarden.ENTITY_TYPES.register("round_knight", () ->
            EntityType.Builder.of(RoundsKnightEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.8F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build(key("round_knight")));

    public static final DeferredHolder<Item, Item> FRAGMENT = DraxAtomic.ITEMS.registerItem("diablos_drop",
            properties -> new Item(properties.rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, Item> SEAL = DraxAtomic.ITEMS.registerItem("diablos_seal",
            properties -> new SealItem(properties.stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, Item> HEART = DraxAtomic.ITEMS.registerItem("diablos_heart",
            properties -> new HeartItem(properties.stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

    static void init(IEventBus modEventBus) {
        modEventBus.addListener(Cult::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(CULTIST.value(), CultistEntity.createAttributes().build());
        event.put(KNIGHT.value(), RoundsKnightEntity.createAttributes().build());
    }

    // ---------------------------------------------------------------- patrullas nocturnas

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!AtomicConfig.cultPatrols()) return;
        ServerLevel level = event.getServer().overworld();
        if (level.getGameTime() % 600 != 0 || level.isBrightOutside()) return;
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextFloat() > AtomicConfig.patrolChance()) continue;
            int nearby = level.getEntitiesOfClass(CultistEntity.class, player.getBoundingBox().inflate(64)).size();
            if (nearby >= 6) continue;
            spawnPatrol(level, player, random);
        }
    }

    private static void spawnPatrol(ServerLevel level, ServerPlayer player, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 24 + random.nextDouble() * 16;
        int cx = (int) (player.getX() + Math.cos(angle) * distance);
        int cz = (int) (player.getZ() + Math.sin(angle) * distance);
        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            int x = cx + random.nextInt(5) - 2;
            int z = cz + random.nextInt(5) - 2;
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.isLoaded(ground) || !level.getBlockState(ground.below()).isSolid()
                    || !level.getFluidState(ground).isEmpty()) continue;
            CultistEntity cultist = CULTIST.value().create(level, EntitySpawnReason.PATROL);
            if (cultist == null) continue;
            cultist.snapTo(x + 0.5, ground.getY(), z + 0.5, random.nextFloat() * 360, 0);
            cultist.setup(i == 0 ? CultistEntity.Kind.MAGE : (random.nextBoolean() ? CultistEntity.Kind.KNIGHT : CultistEntity.Kind.MAGE));
            level.addFreshEntity(cultist);
        }
    }

    // ---------------------------------------------------------------- ítems

    /** Sello de Diablos: clic derecho en el suelo (de noche) invoca al Caballero de la Mesa Redonda. */
    public static final class SealItem extends Item {
        public SealItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Player player = context.getPlayer();
            Level world = context.getLevel();
            if (player == null) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level)) return InteractionResult.SUCCESS;
            if (level.isBrightOutside()) {
                player.sendOverlayMessage(Component.translatable("message.draxatomic.seal_day").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            BlockPos at = context.getClickedPos().above();
            RoundsKnightEntity knight = KNIGHT.value().create(level, EntitySpawnReason.TRIGGERED);
            if (knight == null) return InteractionResult.FAIL;
            knight.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0);
            level.addFreshEntity(knight);
            knight.announce();
            context.getItemInHand().consume(1, player);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic.diablos_seal").withStyle(ChatFormatting.DARK_RED));
        }
    }

    /** Corazón de Diablos: al consumirlo, +50 de poder mágico máximo para siempre. */
    public static final class HeartItem extends Item {
        public static final int BONUS = 50;

        public HeartItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) {
                Mana.addBonus(serverPlayer, BONUS);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE,
                        SoundSource.PLAYERS, 0.8F, 1.3F);
                player.sendOverlayMessage(Component.translatable("message.draxatomic.heart_used", BONUS, Mana.max(player))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                player.getItemInHand(hand).consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic.diablos_heart", BONUS).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
