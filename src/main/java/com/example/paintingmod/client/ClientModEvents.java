package com.example.paintingmod.client;

import com.example.paintingmod.client.CanvasRenderer;
import com.example.paintingmod.registry.ModBlocks;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@OnlyIn(Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        BlockEntityRenderers.register(ModBlocks.CANVAS_BE.get(), CanvasRenderer::new);
    }
}
