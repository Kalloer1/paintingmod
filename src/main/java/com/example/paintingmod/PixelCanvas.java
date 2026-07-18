package com.example.paintingmod;

import com.example.paintingmod.client.ClientModEvents;
import com.example.paintingmod.config.ModConfig;
import com.example.paintingmod.network.ModPackets;
import com.example.paintingmod.registry.ModBlocks;
import com.example.paintingmod.registry.ModCreativeTabs;
import com.example.paintingmod.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(PixelCanvas.MOD_ID)
public class PixelCanvas {
    public static final String MOD_ID = "paintingmod";

    public PixelCanvas(IEventBus modBus, ModContainer container) {
        ModConfig.register(container);
        ModItems.register(modBus);
        ModBlocks.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(ModPackets::registerPayloads);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(ClientModEvents::onClientSetup);
        }
    }
}
