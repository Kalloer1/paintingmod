package com.example.paintingmod.network;

import com.example.paintingmod.PixelCanvas;
import com.example.paintingmod.canvas.CanvasBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: full canvas snapshot (on save / undo / redo). */
public record CanvasUpdatePacket(int[] pixels, BlockPos pos) implements CustomPacketPayload {
    public static final Type<CanvasUpdatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PixelCanvas.MOD_ID, "canvas_update"));

    private static final net.minecraft.network.codec.StreamDecoder<ByteBuf, CanvasUpdatePacket> DECODER = buf -> {
        net.minecraft.network.FriendlyByteBuf fbuf = (net.minecraft.network.FriendlyByteBuf) buf;
        return new CanvasUpdatePacket(fbuf.readVarIntArray(), fbuf.readBlockPos());
    };

    private static final net.minecraft.network.codec.StreamEncoder<ByteBuf, CanvasUpdatePacket> ENCODER = (buf, p) -> {
        net.minecraft.network.FriendlyByteBuf fbuf = (net.minecraft.network.FriendlyByteBuf) buf;
        fbuf.writeVarIntArray(p.pixels());
        fbuf.writeBlockPos(p.pos());
    };

    public static final StreamCodec<ByteBuf, CanvasUpdatePacket> STREAM_CODEC =
            StreamCodec.<ByteBuf, CanvasUpdatePacket>of(ENCODER, DECODER);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CanvasUpdatePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            Level level = player.level();
            if (!(level.getBlockEntity(p.pos()) instanceof CanvasBlockEntity cbe)) return;
            double d = player.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5);
            if (d > 64) return;
            if (!level.mayInteract(player, p.pos())) return;
            if (p.pixels().length != cbe.getWidth() * cbe.getHeight()) return;
            cbe.setPixels(p.pixels());
            cbe.setAuthor(player.getName().getString());
            cbe.markUpdated();
        });
    }
}
