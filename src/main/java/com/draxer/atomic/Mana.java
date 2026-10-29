package com.draxer.atomic;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Poder mágico del jugador: una barra propia (0..máximo) que se recarga sola con el tiempo.
 * Vive en el servidor como attachment del jugador y se envía al cliente para dibujar la barra.
 */
@EventBusSubscriber(modid = DraxAtomic.MOD_ID)
public final class Mana {
    private Mana() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DraxAtomic.MOD_ID);
    /** Se guarda con el jugador y se conserva al morir. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> MANA = ATTACHMENTS.register("mana",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT.fieldOf("value")).copyOnDeath().build());

    /** Poder máximo extra ganado con Corazones de Diablos (permanente). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> BONUS = ATTACHMENTS.register("mana_bonus",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("value")).copyOnDeath().build());

    public static void addBonus(ServerPlayer player, int amount) {
        player.setData(BONUS, player.getData(BONUS) + amount);
        set(player, get(player) + amount);
    }

    public static void refill(ServerPlayer player) {
        set(player, max(player));
    }

    static void init(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /** Máximo: el de la configuración más lo que aporta el traje de slime. */
    public static int max(Player player) {
        return AtomicConfig.maxMana() + player.getData(BONUS) + SlimeSuit.MANA_PER_PIECE * SlimeSuit.pieces(player);
    }

    /** Recarga por segundo: +1 por pieza del traje, y el doble con el set completo. */
    private static int regenPerSecond(Player player) {
        int pieces = SlimeSuit.pieces(player);
        int regen = AtomicConfig.manaRegenPerSecond() + pieces;
        return pieces == 4 ? regen * 2 : regen;
    }

    public static int get(Player player) {
        int value = player.getData(MANA);
        return value < 0 ? max(player) : Math.min(value, max(player));
    }

    private static void set(ServerPlayer player, int value) {
        int clamped = Math.max(0, Math.min(max(player), value));
        player.setData(MANA, clamped);
        sync(player, clamped);
    }

    /** Gasta poder si alcanza (en creativo es gratis). */
    public static boolean spend(ServerPlayer player, int amount) {
        if (player.isCreative()) return true;
        int current = get(player);
        if (current < amount) return false;
        set(player, current - amount);
        return true;
    }

    public static boolean has(Player player, int amount) {
        return player.isCreative() || get(player) >= amount;
    }

    public static void sync(ServerPlayer player) {
        sync(player, get(player));
    }

    private static void sync(ServerPlayer player, int value) {
        PacketDistributor.sendToPlayer(player, new ManaPayload(value, max(player)));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof SlimeSwordItem) return; // no recarga mientras canaliza
        int current = get(player);
        int max = max(player);
        int regen = regenPerSecond(player);
        if (current < max && regen > 0) {
            // Medio segundo de recarga; los impares se reparten entre ticks pares e impares.
            int half = regen / 2 + ((regen % 2 == 1 && player.tickCount % 20 == 0) ? 1 : 0);
            if (half > 0) set(player, current + half);
        } else if (player.tickCount % 40 == 0) {
            sync(player, current); // el máximo puede cambiar al ponerse o quitarse el traje
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
