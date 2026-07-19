package com.example.paintingmod.network;

import com.example.paintingmod.ClientGuiProxy;
import com.example.paintingmod.PixelCanvas;
import com.example.paintingmod.canvas.CanvasBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bidirectional incremental pixel changes during a stroke. Client -> server for the
 *  active painter; server -> client for live preview to nearby players. */
public record CanvasMiniUpdatePacket(int[] indices, int[] values, BlockPos pos) implements CustomPacketPayload {
    public static final Type<CanvasMiniUpdatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PixelCanvas.MOD_ID, "canvas_mini"));

    private static final net.minecraft.network.codec.StreamDecoder<ByteBuf, CanvasMiniUpdatePacket> DECODER = buf -> {
        net.minecraft.network.FriendlyByteBuf fbuf = (net.minecraft.network.FriendlyByteBuf) buf;
        return new CanvasMiniUpdatePacket(fbuf.readVarIntArray(), fbuf.readVarIntArray(), fbuf.readBlockPos());
    };

    private static final net.minecraft.network.codec.StreamEncoder<ByteBuf, CanvasMiniUpdatePacket> ENCODER = (buf, p) -> {
        net.minecraft.network.FriendlyByteBuf fbuf = (net.minecraft.network.FriendlyByteBuf) buf;
        fbuf.writeVarIntArray(p.indices());
        fbuf.writeVarIntArray(p.values());
        fbuf.writeBlockPos(p.pos());
    };

    public static final StreamCodec<ByteBuf, CanvasMiniUpdatePacket> STREAM_CODEC =
            StreamCodec.<ByteBuf, CanvasMiniUpdatePacket>of(ENCODER, DECODER);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CanvasMiniUpdatePacket p, IPayloadContext ctx) {
        if (ctx.flow().isClientbound()) {
            // Client side: apply the broadcast stroke to the local block entity / GUI.
            // Delegated to the common ClientGuiProxy bridge so this packet class never
            // references a client-only type in its constant pool (dedicated-server safe).
            ctx.enqueueWork(() -> ClientGuiProxy.applyMiniUpdate(p));
        } else {
            // Server side: validate and apply, then broadcast to nearby players.
            ctx.enqueueWork(() -> {
                ServerPlayer sender = (ServerPlayer) ctx.player();
                Level level = sender.level();
                if (!(level.getBlockEntity(p.pos()) instanceof CanvasBlockEntity cbe)) return;
                if (!StrokeState.active(sender, p.pos())) return;
                double d = sender.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5);
                if (d > 64) return;
                if (!level.mayInteract(sender, p.pos())) return;
                int n = Math.min(p.indices().length, p.values().length);
                for (int i = 0; i < n; i++) cbe.setPixel(p.indices()[i], p.values()[i]);
                cbe.bumpRenderVersion();

                // broadcast the same mini-update to all nearby players so they see live strokes
                for (ServerPlayer viewer : level.getServer().getPlayerList().getPlayers()) {
                    if (viewer == sender) continue;
                    if (viewer.level() != level) continue;
                    double dv = viewer.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5);
                    if (dv <= 256) { // 16 block radius
                        ModPackets.sendToPlayer(p, viewer);
                    }
                }
            });
        }
    }
}
