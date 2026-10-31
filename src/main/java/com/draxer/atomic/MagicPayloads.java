package com.draxer.atomic;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Mensajes del sistema de magia. */
public final class MagicPayloads {
    private MagicPayloads() {}

    public static final int CAST = 0;
    public static final int NEXT = 1;
    public static final int PREV = 2;
    public static final int COMPRESS_START = 3;
    public static final int COMPRESS_STOP = 4;
    public static final int BUY = 5;
    public static final int SELECT = 6;

    /** Cliente → servidor: una tecla o un botón de la pantalla de habilidades. */
    public record Action(int action, int arg) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Action> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "magic_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::action,
                ByteBufCodecs.VAR_INT, Action::arg,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Action payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) Magic.handle(player, payload.action(), payload.arg());
        }
    }

    /** Servidor → cliente: estado de magia para el HUD y la pantalla de habilidades. */
    public record Sync(int spell, int compression, int toggles, int points, int xp, List<Integer> levels) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Sync> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "magic_sync"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::spell,
                ByteBufCodecs.VAR_INT, Sync::compression,
                ByteBufCodecs.VAR_INT, Sync::toggles,
                ByteBufCodecs.VAR_INT, Sync::points,
                ByteBufCodecs.VAR_INT, Sync::xp,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Sync::levels,
                Sync::new);

        public static volatile Sync latest = new Sync(0, 0, 0, 0, 0, List.of());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        /** Niveles extra de tolerancia a la compresión (va al final de la lista). */
        public int tolerance() {
            int n = MagicState.Upgrade.values().length;
            return levels.size() > n ? levels.get(n) : 0;
        }

        public int level(MagicState.Upgrade upgrade) {
            return upgrade.ordinal() < levels.size() ? levels.get(upgrade.ordinal()) : 0;
        }

        public boolean on(MagicState.Spell spell) {
            return (toggles & spell.bit()) != 0;
        }
    }
}
