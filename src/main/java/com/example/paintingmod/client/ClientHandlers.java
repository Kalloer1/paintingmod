package com.example.paintingmod.client;

import com.example.paintingmod.canvas.CanvasBlockEntity;
import com.example.paintingmod.canvas.PaintingData;
import com.example.paintingmod.network.CanvasOpenPacket;
import com.example.paintingmod.network.CanvasStrokeDeniedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-side handling for network packets (registered lazily from ModPackets via method references). */
@OnlyIn(Dist.CLIENT)
public final class ClientHandlers {
    private ClientHandlers() {}

    public static void handleOpenCanvas(CanvasOpenPacket p, IPayloadContext ctx) {
        // Payload handlers run on the network thread; world/screen access MUST be on the main thread.
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            BlockPos pos = p.pos();
            CanvasBlockEntity be;
            BlockEntity existing = mc.level.getBlockEntity(pos);
            if (existing instanceof CanvasBlockEntity cbe) {
                be = cbe;
            } else {
                BlockState state = mc.level.getBlockState(pos);
                be = new CanvasBlockEntity(pos, state, p.width(), p.height());
            }
            be.setPixels(p.pixels());
            be.setChanged();

            mc.setScreen(new DrawingScreen(new PaintingData(p.width(), p.height(), p.pixels()),
                    (w, h, px) -> ClientHandlers.sendToServer(new com.example.paintingmod.network.CanvasUpdatePacket(px, pos)),
                    pos, true));
        });
    }

    public static void handleDenied(CanvasStrokeDeniedPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            BlockEntity e = mc.level.getBlockEntity(p.pos());
            if (e instanceof CanvasBlockEntity cbe) cbe.setPixels(p.pixels());
            if (mc.screen instanceof DrawingScreen ds && ds.getPos() != null && ds.getPos().equals(p.pos()))
                ds.reloadPixels(p.pixels());
            if (mc.player != null)
                mc.player.sendSystemMessage(Component.literal("§c染料不足，本笔已回滚"));
        });
    }

    public static void sendToServer(CustomPacketPayload packet) {
        Minecraft.getInstance().getConnection().send(packet);
    }
}
