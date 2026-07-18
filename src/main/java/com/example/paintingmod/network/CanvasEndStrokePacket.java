package com.example.paintingmod.network;

import com.example.paintingmod.PixelCanvas;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: a stroke ended on a placed canvas. Dyes were already charged at
 * begin time, so here we just clear the server-side stroke state. Pixel data was carried
 * live by the mini-updates; no full snapshot is re-applied here (which keeps a denied
 * stroke from being resurrected by a late save).
 */
public record CanvasEndStrokePacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<CanvasEndStrokePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PixelCanvas.MOD_ID, "canvas_end"));

    public static final StreamCodec<ByteBuf, CanvasEndStrokePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> ((FriendlyByteBuf) buf).writeBlockPos(p.pos()),
            buf -> new CanvasEndStrokePacket(((FriendlyByteBuf) buf).readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CanvasEndStrokePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            StrokeState.end(player);
        });
    }
}
