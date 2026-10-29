package com.draxer.atomic;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mitsugoshi: la compañía de comercio. El Mostrador Mitsugoshi es el puesto de trabajo de los
 * empleados (una profesión de aldeano), que venden chocolate, café, champú y equipo de la Shadow
 * Garden. Las tiendas y la capital aparecen en el mundo como estructuras (ver data/worldgen).
 */
public final class Mitsugoshi {
    private Mitsugoshi() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, DraxAtomic.MOD_ID);
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, DraxAtomic.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, DraxAtomic.MOD_ID);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, path);
    }

    // ---------------------------------------------------------------- el mostrador y la profesión

    public static final DeferredHolder<Block, Block> COUNTER = BLOCKS.register("mitsugoshi_counter", () ->
            new Block(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id("mitsugoshi_counter")))
                    .strength(2.0F).sound(SoundType.WOOD)));
    public static final DeferredHolder<Item, Item> COUNTER_ITEM = DraxAtomic.ITEMS.registerItem("mitsugoshi_counter",
            properties -> new BlockItem(COUNTER.value(), properties.useBlockDescriptionPrefix()));

    public static final DeferredHolder<PoiType, PoiType> CLERK_POI = POI_TYPES.register("mitsugoshi_clerk", () ->
            new PoiType(Set.copyOf(COUNTER.value().getStateDefinition().getPossibleStates()), 1, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> CLERK = PROFESSIONS.register("mitsugoshi_clerk", () ->
            new VillagerProfession(
                    Component.translatable("entity.draxatomic.villager.mitsugoshi_clerk"),
                    poi -> poi.is(CLERK_POI.getKey()),
                    poi -> poi.is(CLERK_POI.getKey()),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN,
                    trades()));

    private static Int2ObjectMap<ResourceKey<TradeSet>> trades() {
        Int2ObjectMap<ResourceKey<TradeSet>> map = new Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= 5; level++) {
            map.put(level, ResourceKey.create(Registries.TRADE_SET, id("mitsugoshi_clerk/level_" + level)));
        }
        return map;
    }

    // ---------------------------------------------------------------- productos

    public static final DeferredHolder<Item, Item> CHOCOLATE = DraxAtomic.ITEMS.registerItem("mitsugoshi_chocolate",
            properties -> new Product(properties.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.9F).alwaysEdible().build())
                    .rarity(Rarity.UNCOMMON), "mitsugoshi_chocolate"));
    public static final DeferredHolder<Item, Item> COFFEE = DraxAtomic.ITEMS.registerItem("mitsugoshi_coffee",
            properties -> new Product(properties.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).alwaysEdible().build())
                    .stacksTo(16).rarity(Rarity.UNCOMMON), "mitsugoshi_coffee"));
    public static final DeferredHolder<Item, Item> SHAMPOO = DraxAtomic.ITEMS.registerItem("mitsugoshi_shampoo",
            properties -> new Product(properties.food(new FoodProperties.Builder().nutrition(0).saturationModifier(0.0F).alwaysEdible().build())
                    .stacksTo(16).rarity(Rarity.RARE), "mitsugoshi_shampoo"));

    /** Un producto de Mitsugoshi: al consumirlo aplica su efecto. */
    public static final class Product extends Item {
        private final String id;

        public Product(Properties properties, String id) {
            super(properties);
            this.id = id;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide()) {
                switch (id) {
                    case "mitsugoshi_chocolate" -> {
                        entity.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 1));
                        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
                    }
                    case "mitsugoshi_coffee" -> {
                        entity.addEffect(new MobEffectInstance(MobEffects.HASTE, 2400, 1));
                        entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0));
                    }
                    case "mitsugoshi_shampoo" -> {
                        // "Brillo Mitsugoshi": limpia los efectos negativos y da suerte.
                        java.util.List<Holder<MobEffect>> bad = new java.util.ArrayList<>();
                        for (MobEffectInstance e : entity.getActiveEffects()) {
                            if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(e.getEffect());
                        }
                        bad.forEach(entity::removeEffect);
                        entity.addEffect(new MobEffectInstance(MobEffects.LUCK, 6000, 1));
                        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
                    }
                    default -> {}
                }
            }
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic." + id).withStyle(ChatFormatting.GOLD));
        }
    }

    static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        POI_TYPES.register(modEventBus);
        PROFESSIONS.register(modEventBus);
    }
}
