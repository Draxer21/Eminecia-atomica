package com.draxer.atomic;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** La Shadow Garden: siete aliadas que se invocan con el Emblema de la Sombra. */
public final class ShadowGarden {
    private ShadowGarden() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DraxAtomic.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ShadowAllyEntity>> ALLY = ENTITY_TYPES.register("shadow_ally", () ->
            EntityType.Builder.of(ShadowAllyEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "shadow_ally"))));

    public static final DeferredHolder<Item, Item> EMBLEM = DraxAtomic.ITEMS.registerItem("shadow_emblem",
            properties -> new EmblemItem(properties.stacksTo(16).rarity(Rarity.EPIC)));

    static void init(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ShadowGarden::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ALLY.value(), ShadowAllyEntity.createAttributes().build());
    }

    /** Las siete integrantes y su estilo de combate. */
    public enum Member {
        NOCTIS("noctis", Role.SWORD, 0x2B2F8A),
        UMBRA("umbra", Role.STEALTH, 0x1A1A1A),
        VESPER("vesper", Role.MAGE, 0xC9B26A),
        LYRA("lyra", Role.ARCHER, 0x6FA8D9),
        SELENE("selene", Role.HEALER, 0xE8E8F0),
        NYX("nyx", Role.BERSERKER, 0xA02A2A),
        IRIS("iris", Role.GUARDIAN, 0x6A4BA8);

        public final String id;
        public final Role role;
        /** Color de pelo (para la textura y las partículas). */
        public final int hair;

        Member(String id, Role role, int hair) {
            this.id = id;
            this.role = role;
            this.hair = hair;
        }

        public static Member of(int index) {
            Member[] all = values();
            return all[Math.floorMod(index, all.length)];
        }

        public Component displayName() {
            return Component.translatable("entity.draxatomic.member." + id);
        }
    }

    public enum Role { SWORD, STEALTH, MAGE, ARCHER, HEALER, BERSERKER, GUARDIAN }

    /** Emblema de la Sombra: clic derecho en el suelo invoca a la siguiente integrante. */
    public static final class EmblemItem extends Item {
        private static final String NEXT = "DraxNextMember";

        public EmblemItem(Properties properties) {
            super(properties);
        }

        static Member next(ItemStack stack) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            return Member.of(data == null ? 0 : data.copyTag().getInt(NEXT).orElse(0));
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Player player = context.getPlayer();
            if (player == null) return InteractionResult.PASS;
            if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
            ItemStack stack = context.getItemInHand();
            Member member = next(stack);
            BlockPos at = context.getClickedPos().relative(context.getClickedFace());
            ShadowAllyEntity ally = ALLY.value().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (ally == null) return InteractionResult.FAIL;
            ally.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
            ally.setup(member, player);
            if (!level.addFreshEntity(ally)) return InteractionResult.FAIL;
            ally.arrive();
            // La próxima vez, la siguiente integrante.
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.putInt(NEXT, (member.ordinal() + 1) % Member.values().length);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            stack.consume(1, player);
            player.sendOverlayMessage(Component.translatable("message.draxatomic.ally_joined", member.displayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            Member member = next(stack);
            tooltip.accept(Component.translatable("tooltip.draxatomic.shadow_emblem.next", member.displayName(),
                    Component.translatable("role.draxatomic." + member.role.name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.accept(Component.translatable("tooltip.draxatomic.shadow_emblem.help").withStyle(ChatFormatting.GRAY));
        }
    }
}
