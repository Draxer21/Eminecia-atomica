package com.draxer.atomic;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Torneo de espadas de la academia de Midgar: con la Inscripción, dentro de Midgar, peleas cuatro
 * rondas seguidas contra rivales cada vez más fuertes. Si ganas la final, eres campeón.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Tournament {
    private Tournament() {}

    public static final int ROUNDS = 4;

    public static final DeferredHolder<EntityType<?>, EntityType<DuelistEntity>> DUELIST = ShadowGarden.ENTITY_TYPES.register("duelist", () ->
            EntityType.Builder.of(DuelistEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "duelist"))));

    public static final DeferredHolder<Item, Item> ENTRY = DraxAtomic.ITEMS.registerItem("tournament_entry",
            properties -> new EntryItem(properties.stacksTo(16).rarity(Rarity.UNCOMMON)));

    /** Veces que el jugador fue campeón (se conserva). */
    public static final DeferredHolder<net.neoforged.neoforge.attachment.AttachmentType<?>, net.neoforged.neoforge.attachment.AttachmentType<Integer>> TITLES =
            Mana.ATTACHMENTS.register("tournament_titles",
                    () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("value")).copyOnDeath().build());

    static void init(IEventBus modEventBus) {
        modEventBus.addListener((EntityAttributeCreationEvent event) -> event.put(DUELIST.value(), DuelistEntity.createAttributes().build()));
    }

    private record Match(ServerLevel level, Vec3 arena, int[] round, int[] wait) {}

    private static final Map<UUID, Match> MATCHES = new HashMap<>();

    static boolean start(ServerLevel level, ServerPlayer player) {
        if (MATCHES.containsKey(player.getUUID())) return false;
        Vec3 arena = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(6));
        MATCHES.put(player.getUUID(), new Match(level, arena, new int[] {1}, new int[] {20}));
        player.sendSystemMessage(Component.translatable("message.draxatomic.tournament_start").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5F, 1.0F);
        return true;
    }

    private static void spawnRival(Match match, ServerPlayer player) {
        DuelistEntity rival = DUELIST.value().create(match.level(), EntitySpawnReason.EVENT);
        if (rival == null) return;
        rival.snapTo(match.arena().x, player.getY(), match.arena().z, player.getYRot() + 180.0F, 0);
        rival.setup(player, match.round()[0]);
        match.level().addFreshEntity(rival);
        Component name = Component.translatable("entity.draxatomic.duelist." + rival.skin());
        rival.setCustomName(name.copy().withStyle(match.round()[0] == ROUNDS ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        player.sendSystemMessage(Component.translatable(match.round()[0] == ROUNDS ? "message.draxatomic.tournament_final"
                : "message.draxatomic.tournament_round", match.round()[0], name).withStyle(ChatFormatting.YELLOW));
        match.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    /** Un rival cayó: se pasa a la siguiente ronda o se corona al campeón. */
    static void roundWon(ServerLevel level, DuelistEntity rival) {
        if (rival.challenger() == null) return;
        Match match = MATCHES.get(rival.challenger());
        if (match == null || !(level.getEntity(rival.challenger()) instanceof ServerPlayer player)) return;
        if (match.round()[0] >= ROUNDS) {
            MATCHES.remove(player.getUUID());
            crown(level, player);
            return;
        }
        match.round()[0]++;
        match.wait()[0] = 60;
        player.heal(6.0F);
        player.sendOverlayMessage(Component.translatable("message.draxatomic.tournament_next").withStyle(ChatFormatting.GREEN));
    }

    private static void crown(ServerLevel level, ServerPlayer player) {
        player.setData(TITLES, player.getData(TITLES) + 1);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(player) < 96 * 96) {
                p.sendSystemMessage(Component.translatable("message.draxatomic.tournament_champion", player.getDisplayName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
        }
        give(player, new ItemStack(Items.DIAMOND, 6));
        give(player, new ItemStack(Items.EMERALD, 32));
        give(player, new ItemStack(Items.GOLDEN_APPLE, 2));
        Magic.addPoints(player, 2);
        Mana.addBonus(player, 20);
        Achievements.grant(player, "champion");
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void forfeit(UUID id, ServerLevel level, String reason) {
        MATCHES.remove(id);
        for (DuelistEntity d : level.getEntities(DUELIST.value(), d -> id.equals(d.challenger()))) d.discard();
        if (level.getEntity(id) instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable(reason).withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (MATCHES.isEmpty()) return;
        for (var entry : new HashMap<>(MATCHES).entrySet()) {
            Match match = entry.getValue();
            if (!(match.level().getEntity(entry.getKey()) instanceof ServerPlayer player)) {
                forfeit(entry.getKey(), match.level(), "message.draxatomic.tournament_lost");
                continue;
            }
            if (player.position().distanceTo(match.arena()) > 40) {
                forfeit(entry.getKey(), match.level(), "message.draxatomic.tournament_fled");
                continue;
            }
            if (match.wait()[0] > 0 && --match.wait()[0] == 0) spawnRival(match, player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && MATCHES.containsKey(player.getUUID())) {
            forfeit(player.getUUID(), (ServerLevel) player.level(), "message.draxatomic.tournament_lost");
        }
    }

    /** Inscripción al torneo: úsala dentro de Midgar. */
    public static final class EntryItem extends Item {
        public EntryItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            if (Cities.siteAt(server, CityLayout.Kind.MIDGAR, sp.getX(), sp.getZ()).isEmpty() && !sp.isCreative()) {
                sp.sendOverlayMessage(Component.translatable("message.draxatomic.tournament_where").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            if (!start(server, sp)) return InteractionResult.FAIL;
            player.getItemInHand(hand).consume(1, player);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.draxatomic.tournament_entry").withStyle(ChatFormatting.GOLD));
        }
    }
}
