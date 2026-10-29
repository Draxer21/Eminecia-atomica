package com.draxer.atomic;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Servidor → cliente: poder mágico actual y máximo, para la barra. */
public record ManaPayload(int mana, int max) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ManaPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "mana"));
    public static final StreamCodec<ByteBuf, ManaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ManaPayload::mana,
            ByteBufCodecs.VAR_INT, ManaPayload::max,
            ManaPayload::new);

    /** Último valor recibido (solo cliente). */
    public static volatile ManaPayload latest = new ManaPayload(100, 100);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
