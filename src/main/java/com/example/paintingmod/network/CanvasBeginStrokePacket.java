package com.example.paintingmod.network;

import com.example.paintingmod.PixelCanvas;
import com.example.paintingmod.canvas.CanvasBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: a stroke is starting on a placed canvas. Painting is free, so there is
 * no dye charge here — the server only opens the stroke session (used to validate live
 * mini-updates from this player and to roll back on disconnect) and checks interaction.
 */
public record CanvasBeginStrokePacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<CanvasBeginStrokePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PixelCanvas.MOD_ID, "canvas_begin"));

    public static final StreamCodec<ByteBuf, CanvasBeginStrokePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> ((FriendlyByteBuf) buf).writeBlockPos(p.pos()),
            buf -> new CanvasBeginStrokePacket(((FriendlyByteBuf) buf).readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CanvasBeginStrokePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            Level level = player.level();
            if (!(level.getBlockEntity(p.pos()) instanceof CanvasBlockEntity cbe)) return;
            double d = player.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5);
            if (d > 64) return;
            if (!level.mayInteract(player, p.pos())) return;
            StrokeState.begin(player, cbe);
        });
    }
}
