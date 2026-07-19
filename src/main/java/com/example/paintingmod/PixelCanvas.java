package com.example.paintingmod;

import com.example.paintingmod.config.ModConfig;
import com.example.paintingmod.network.ModPackets;
import com.example.paintingmod.registry.ModBlocks;
import com.example.paintingmod.registry.ModCreativeTabs;
import com.example.paintingmod.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(PixelCanvas.MOD_ID)
public class PixelCanvas {
    public static final String MOD_ID = "paintingmod";

    public PixelCanvas(IEventBus modBus, ModContainer container) {
        ModConfig.register(container);
        ModItems.register(modBus);
        ModBlocks.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(ModPackets::registerPayloads);
        // 客户端专属初始化（渲染器注册等）放在 ClientModEvents 里，该类用
        // @EventBusSubscriber(dist = Dist.CLIENT) 标注，只在客户端被加载与注册。
        // 这个公共构造器不再引用任何客户端类，专用服务器绝不会触达客户端代码。
    }
}
