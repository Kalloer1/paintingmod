package com.example.paintingmod.registry;

import com.example.paintingmod.PixelCanvas;
import com.example.paintingmod.canvas.MagicCanvasStubItem;
import com.example.paintingmod.canvas.PaintPaperItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    private static final String MOD_ID = PixelCanvas.MOD_ID;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredHolder<Item, Item> UNIVERSAL_DYE =
            ITEMS.register("universal_dye", () -> new Item(new Item.Properties()));

    /** The placeable wall canvas. Craftable so players can mount a blank canvas directly. */
    public static final DeferredHolder<Item, BlockItem> CANVAS_BLOCK_ITEM =
            ITEMS.register("canvas_block", () -> new BlockItem(ModBlocks.CANVAS_BLOCK.get(), new Item.Properties()));

    /** Handheld paint paper: right-click opens the drawing GUI directly, no placement needed. */
    public static final DeferredHolder<Item, PaintPaperItem> PAINT_PAPER =
            ITEMS.register("paint_paper", () -> new PaintPaperItem(new Item.Properties()));

    public static final DeferredHolder<Item, MagicCanvasStubItem> MAGIC_CANVAS_STUB =
            ITEMS.register("magic_canvas_stub", () -> new MagicCanvasStubItem(new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
