package com.example.paintingmod.network;

import com.example.paintingmod.PixelCanvas;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client: a stroke could not be paid for. Carries the rolled-back pixels so
 * the open GUI can restore its own buffer. The world canvas is restored via the normal
 * block update that accompanied the rollback.
 */
public record CanvasStrokeDeniedPacket(BlockPos pos, int[] pixels) implements CustomPacketPayload {
    public static final Type<CanvasStrokeDeniedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PixelCanvas.MOD_ID, "canvas_denied"));

    public static final StreamCodec<ByteBuf, CanvasStrokeDeniedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf fbb = (FriendlyByteBuf) buf;
                fbb.writeBlockPos(p.pos());
                fbb.writeVarIntArray(p.pixels());
            },
            buf -> {
                FriendlyByteBuf fbb = (FriendlyByteBuf) buf;
                return new CanvasStrokeDeniedPacket(fbb.readBlockPos(), fbb.readVarIntArray());
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
