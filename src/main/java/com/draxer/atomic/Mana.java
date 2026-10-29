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
        return AtomicConfig.maxMana() + player.getData(BONUS) + SlimeSuit.MANA_PER_PIECE * SlimeSuit.pieces(player) + Magic.maxManaBonus(player);
    }

    /** Recarga por segundo: +1 por pieza del traje, y el doble con el set completo. */
    private static int regenPerSecond(Player player) {
        int pieces = SlimeSuit.pieces(player);
        int regen = AtomicConfig.manaRegenPerSecond() + pieces + Magic.regenBonus(player);
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
    /**
     * Gasta poder para un ataque o hechizo. Si no alcanza y la Sobrecarga está activa, el resto se
     * paga con vida (5 de poder = 1 punto de vida). Cuenta como experiencia mágica.
     */
    public static boolean spend(ServerPlayer player, int amount) {
        if (player.isCreative() || amount <= 0) return true;
        int current = get(player);
        if (current < amount) {
            int hp = overloadCost(player, amount - current);
            if (hp < 0) return false;
            set(player, 0);
            player.setHealth(player.getHealth() - hp);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.WITHER_HURT, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
            Magic.addXp(player, amount);
            return true;
        }
        set(player, current - amount);
        Magic.addXp(player, amount);
        return true;
    }

    /** Gasto continuo (hechizos sostenidos, compresión, planeo): sin Sobrecarga ni experiencia. */
    public static boolean drain(ServerPlayer player, int amount) {
        if (player.isCreative() || amount <= 0) return true;
        int current = get(player);
        if (current < amount) return false;
        set(player, current - amount);
        return true;
    }

    /** El Caballero de la Mesa Redonda te roba poder con sus golpes. */
    public static void steal(ServerPlayer player, int amount) {
        if (player.isCreative()) return;
        set(player, get(player) - amount);
    }

    /** Vida que costaría cubrir {@code deficit} con Sobrecarga, o -1 si no se puede. */
    private static int overloadCost(Player player, int deficit) {
        if (!Magic.state(player).on(MagicState.Spell.OVERLOAD)) return -1;
        int hp = (int) Math.ceil(deficit / 5.0);
        return player.getHealth() > hp + 1 ? hp : -1;
    }

    public static boolean has(Player player, int amount) {
        return player.isCreative() || get(player) >= amount || overloadCost(player, amount - get(player)) >= 0;
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
