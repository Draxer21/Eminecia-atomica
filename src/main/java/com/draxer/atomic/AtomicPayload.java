package com.draxer.atomic;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Servidor → cliente: dónde estalló la explosión y de qué tamaño, para la esfera de luz. */
public record AtomicPayload(double x, double y, double z, int radius, int color, boolean big) implements CustomPacketPayload {
    public static final int VIOLET = 0x8A3BFF;
    public static final int RECOVERY = 0x7CFFB8;
    public static final CustomPacketPayload.Type<AtomicPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "atomic"));
    public static final StreamCodec<ByteBuf, AtomicPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, AtomicPayload::x,
            ByteBufCodecs.DOUBLE, AtomicPayload::y,
            ByteBufCodecs.DOUBLE, AtomicPayload::z,
            ByteBufCodecs.VAR_INT, AtomicPayload::radius,
            ByteBufCodecs.INT, AtomicPayload::color,
            ByteBufCodecs.BOOL, AtomicPayload::big,
            AtomicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = DraxAtomic.MOD_ID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1")
                    .playToClient(TYPE, STREAM_CODEC)
                    .playToClient(ManaPayload.TYPE, ManaPayload.STREAM_CODEC)
                    .playToServer(DoubleJumpPayload.TYPE, DoubleJumpPayload.STREAM_CODEC, DoubleJumpPayload::handle)
                    .playToServer(MagicPayloads.Action.TYPE, MagicPayloads.Action.STREAM_CODEC, MagicPayloads.Action::handle)
                    .playToClient(MagicPayloads.Sync.TYPE, MagicPayloads.Sync.STREAM_CODEC);
        }
    }
}
