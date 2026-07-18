package com.example.paintingmod.canvas;

import com.example.paintingmod.client.DrawingScreen;
import com.example.paintingmod.config.ModConfig;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A handheld paint paper. Right-click opens the pixel-paint GUI directly on the client
 * — no placement, no block entity, no server round-trip needed to start drawing.
 *
 * The appraise button (inside the GUI) still runs the full AI flow: it snapshots the
 * pixels and sends a {@code CanvasAppraisalPacket} with an absent position. The server
 * handles that identically to the wall canvas (grants the reward, stores a magic-canvas
 * stub), only skipping the "clear the wall pixels" step because there is no wall block.
 */
public class PaintPaperItem extends Item {

    public PaintPaperItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Open the GUI on the client only. The guarded branch is never reached on a server,
        // so the client-only DrawingScreen / Minecraft references are never linked there.
        if (level.isClientSide()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            int size = Math.max(16, Math.min(ModConfig.maxCanvasSize.get(), ModConfig.defaultCanvasSize.get()));
            PaintingData data = new PaintingData(size, size); // blank, filled with Palette.BACKING
            // pos = null -> handheld session. onSave is a no-op: the appraise button ships the
            // pixels to the server itself, so there is nothing to mirror back onto a block.
            mc.setScreen(new DrawingScreen(data, (w, h, px) -> { }, null, true));
        }
        // SUCCESS (client side) stops the item being "used up" and keeps it reusable.
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
