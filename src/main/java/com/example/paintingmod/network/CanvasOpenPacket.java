package com.example.paintingmod.network;

import com.example.paintingmod.PixelCanvas;
import com.example.paintingmod.canvas.CanvasBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client: open the drawing GUI, carrying the full canvas state so the
 * client screen is immediately correct even if the BE update packet has not arrived.
 */
public record CanvasOpenPacket(BlockPos pos, int width, int height, int[] pixels) implements CustomPacketPayload {
    public static final Type<CanvasOpenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PixelCanvas.MOD_ID, "canvas_open"));

    public static final StreamCodec<ByteBuf, CanvasOpenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf fbb = (FriendlyByteBuf) buf;
                fbb.writeBlockPos(p.pos());
                fbb.writeVarInt(p.width());
                fbb.writeVarInt(p.height());
                fbb.writeVarInt(p.pixels.length);
                for (int v : p.pixels) fbb.writeVarInt(v);
            },
            buf -> {
                FriendlyByteBuf fbb = (FriendlyByteBuf) buf;
                BlockPos pos = fbb.readBlockPos();
                int w = fbb.readVarInt();
                int h = fbb.readVarInt();
                int plen = fbb.readVarInt();
                int[] pixels = new int[plen];
                for (int i = 0; i < plen; i++) pixels[i] = fbb.readVarInt();
                return new CanvasOpenPacket(pos, w, h, pixels);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static CanvasOpenPacket fromBlockEntity(CanvasBlockEntity be) {
        return new CanvasOpenPacket(be.getBlockPos(), be.getWidth(), be.getHeight(), be.getPixels().clone());
    }
}
