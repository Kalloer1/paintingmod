package com.example.paintingmod.client;

import com.example.paintingmod.canvas.CanvasBlockEntity;
import com.example.paintingmod.network.CanvasMiniUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side application of a broadcast pixel mini-update. Isolated in the {@code client}
 * package and marked {@link OnlyIn}({@link Dist#CLIENT}); invoked only from
 * {@code CanvasMiniUpdatePacket.handle()} via a {@code DistExecutor.runWhenOn} lambda so the
 * common packet class never holds a direct reference to this client-only class.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientMiniUpdateHandler {
    private ClientMiniUpdateHandler() {}

    public static void applyToClient(CanvasMiniUpdatePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockEntity e = mc.level.getBlockEntity(p.pos());
        if (!(e instanceof CanvasBlockEntity cbe)) return;
        int n = Math.min(p.indices().length, p.values().length);
        for (int i = 0; i < n; i++) cbe.setPixel(p.indices()[i], p.values()[i]);
        cbe.bumpRenderVersion();
        if (mc.screen instanceof DrawingScreen ds && ds.getPos() != null && ds.getPos().equals(p.pos()))
            ds.reloadPixels(cbe.getPixels());
    }
}
