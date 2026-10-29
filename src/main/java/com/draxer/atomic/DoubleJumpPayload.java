package com.draxer.atomic;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente → servidor: el jugador hizo doble salto con las botas de slime. */
public record DoubleJumpPayload() implements CustomPacketPayload {
    public static final DoubleJumpPayload INSTANCE = new DoubleJumpPayload();
    public static final CustomPacketPayload.Type<DoubleJumpPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DraxAtomic.MOD_ID, "double_jump"));
    public static final StreamCodec<ByteBuf, DoubleJumpPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(DoubleJumpPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) SlimeSuit.doubleJump(player);
    }
}
