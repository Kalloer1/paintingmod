package com.example.paintingmod.client;

import com.example.paintingmod.canvas.ItemNBT;
import com.example.paintingmod.canvas.PaintingData;
import com.example.paintingmod.config.ModConfig;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only helpers for opening the drawing GUI. These are deliberately isolated in the
 * {@code client} package and marked {@link OnlyIn}({@link Dist#CLIENT}) so they are never
 * linked from common-side code. Common item classes call into them through
 * {@code DistExecutor.runWhenOn(Dist.CLIENT, ...)} lambdas, which keeps every reference to
 * this class inside a lambda's constant pool — never in the common class's — so a dedicated
 * server never touches a client-only class at load time.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientGuiOpener {
    private ClientGuiOpener() {}

    /** Open a blank, handheld paint-paper drawing session. */
    public static void openPaintGui() {
        int size = Math.max(16, Math.min(ModConfig.maxCanvasSize.get(), ModConfig.defaultCanvasSize.get()));
        PaintingData data = new PaintingData(size, size); // blank, filled with Palette.BACKING
        net.minecraft.client.Minecraft.getInstance().setScreen(new DrawingScreen(data, (w, h, px) -> { }, null, true));
    }

    /** Open a magic-canvas stub: show its stored artwork, or a blank canvas if empty. */
    public static void openStubGui(ItemStack stack) {
        PaintingData data;
        if (ItemNBT.hasTag(stack, "Painting")) {
            data = PaintingData.load(ItemNBT.getTag(stack, "Painting"));
        } else {
            int size = Math.max(16, Math.min(ModConfig.maxCanvasSize.get(), ModConfig.defaultCanvasSize.get()));
            data = new PaintingData(size, size);
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(new DrawingScreen(data, (w, h, px) -> {
            PaintingData saved = new PaintingData(w, h, px);
            ItemNBT.putTag(stack, "Painting", saved.save());
        }, null, false));
    }
}
