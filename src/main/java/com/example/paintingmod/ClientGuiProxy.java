package com.example.paintingmod;

import com.example.paintingmod.network.CanvasMiniUpdatePacket;
import com.example.paintingmod.network.CanvasOpenPacket;
import com.example.paintingmod.network.CanvasStrokeDeniedPacket;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Side-agnostic bridge between common code (item {@code use()}, packet registration) and the
 * client-only GUI / network handlers.
 *
 * <p>This class lives in the common package and references ONLY common types — it never
 * imports or names a client-only class — so a dedicated server loads it without ever
 * touching {@code net.minecraft.client.*}. The actual client behavior is injected at
 * client startup (see {@code ClientModEvents}) through the nullable fields below; on a
 * dedicated server those fields stay {@code null} and every method becomes a safe no-op.</p>
 *
 * <p>This is the correct NeoForge 1.21 replacement for the removed {@code DistExecutor}:
 * instead of a common class holding a hard (constant-pool) reference to a client class,
 * it holds only {@code Runnable}/{@code Consumer} references that are populated on the
 * client side.</p>
 */
public final class ClientGuiProxy {
    private ClientGuiProxy() {}

    @Nullable private static Runnable paintGuiOpener = null;
    @Nullable private static Consumer<ItemStack> stubGuiOpener = null;
    @Nullable private static Consumer<CanvasMiniUpdatePacket> miniUpdateApplier = null;
    @Nullable private static BiConsumer<CanvasOpenPacket, IPayloadContext> openCanvasHandler = null;
    @Nullable private static BiConsumer<CanvasStrokeDeniedPacket, IPayloadContext> deniedHandler = null;

    /** Called once from client-only init to wire up the real client behavior. */
    public static void initClient(
            Runnable paintGuiOpener,
            Consumer<ItemStack> stubGuiOpener,
            Consumer<CanvasMiniUpdatePacket> miniUpdateApplier,
            BiConsumer<CanvasOpenPacket, IPayloadContext> openCanvasHandler,
            BiConsumer<CanvasStrokeDeniedPacket, IPayloadContext> deniedHandler) {
        ClientGuiProxy.paintGuiOpener = paintGuiOpener;
        ClientGuiProxy.stubGuiOpener = stubGuiOpener;
        ClientGuiProxy.miniUpdateApplier = miniUpdateApplier;
        ClientGuiProxy.openCanvasHandler = openCanvasHandler;
        ClientGuiProxy.deniedHandler = deniedHandler;
    }

    public static void openPaintGui() {
        if (paintGuiOpener != null) paintGuiOpener.run();
    }

    public static void openStubGui(ItemStack stack) {
        if (stubGuiOpener != null) stubGuiOpener.accept(stack);
    }

    public static void applyMiniUpdate(CanvasMiniUpdatePacket p) {
        if (miniUpdateApplier != null) miniUpdateApplier.accept(p);
    }

    public static void handleOpenCanvas(CanvasOpenPacket p, IPayloadContext ctx) {
        if (openCanvasHandler != null) openCanvasHandler.accept(p, ctx);
    }

    public static void handleDenied(CanvasStrokeDeniedPacket p, IPayloadContext ctx) {
        if (deniedHandler != null) deniedHandler.accept(p, ctx);
    }
}
