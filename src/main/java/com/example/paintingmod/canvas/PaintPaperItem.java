package com.example.paintingmod.canvas;

import com.example.paintingmod.ClientGuiProxy;
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
 * The GUI open is delegated to {@link ClientGuiProxy#openPaintGui()}, a common-side bridge
 * that resolves to the real client-only screen opener only on the client. This item class
 * therefore never references a client-only type, so it loads safely on a dedicated server.
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
        // Only ever true on the client; the proxy is a common bridge, so no client type is
        // referenced from this common item class.
        if (level.isClientSide()) ClientGuiProxy.openPaintGui();
        // SUCCESS (client side) stops the item being "used up" and keeps it reusable.
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
