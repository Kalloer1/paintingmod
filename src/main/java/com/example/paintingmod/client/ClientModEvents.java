package com.example.paintingmod.client;

import com.example.paintingmod.PixelCanvas;
import com.example.paintingmod.client.CanvasRenderer;
import com.example.paintingmod.registry.ModBlocks;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only mod event handlers.
 *
 * <p>Annotated with {@link EventBusSubscriber} restricted to {@link Dist#CLIENT} so the class
 * is only registered (and, thanks to {@link OnlyIn}, only present) on the client. The dedicated
 * server never loads this class, which is the correct way to keep client-side setup isolated
 * from the server.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = PixelCanvas.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        BlockEntityRenderers.register(ModBlocks.CANVAS_BE.get(), CanvasRenderer::new);
    }
}
