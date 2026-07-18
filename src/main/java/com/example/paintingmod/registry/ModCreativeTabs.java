package com.example.paintingmod.registry;

import com.example.paintingmod.PixelCanvas;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    private static final String MOD_ID = PixelCanvas.MOD_ID;

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("paintingmod_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.paintingmod"))
                    .icon(() -> new ItemStack(ModItems.CANVAS_BLOCK_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.PAINT_PAPER.get());
                        output.accept(ModItems.CANVAS_BLOCK_ITEM.get());
                        output.accept(ModItems.UNIVERSAL_DYE.get());
                        output.accept(ModItems.MAGIC_CANVAS_STUB.get());
                    })
                    .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
