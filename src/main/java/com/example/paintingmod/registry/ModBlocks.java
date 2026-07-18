package com.example.paintingmod.registry;

import com.example.paintingmod.PixelCanvas;
import com.example.paintingmod.canvas.CanvasBlock;
import com.example.paintingmod.canvas.CanvasBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.createBlocks(PixelCanvas.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, PixelCanvas.MOD_ID);

    public static final DeferredHolder<Block, CanvasBlock> CANVAS_BLOCK =
            BLOCKS.register("canvas_block", () -> new CanvasBlock(
                    Block.Properties.of().strength(0.2f).noOcclusion().sound(SoundType.WOOD)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CanvasBlockEntity>> CANVAS_BE =
            BLOCK_ENTITIES.register("canvas", () -> BlockEntityType.Builder.of(
                            CanvasBlockEntity::new, CANVAS_BLOCK.get())
                    .build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
