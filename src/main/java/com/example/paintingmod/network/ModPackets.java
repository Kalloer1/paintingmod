package com.example.paintingmod.network;

import com.example.paintingmod.PixelCanvas;
import com.example.paintingmod.client.ClientHandlers;
import com.example.paintingmod.network.CanvasAppraisalPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Central network payload registration (common side). Method references to client-only
 *  handlers are lazy and will not trigger class loading on a dedicated server. */
public final class ModPackets {

    private ModPackets() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent ev) {
        var r = ev.registrar(PixelCanvas.MOD_ID);
        // client -> server
        r.playToServer(CanvasUpdatePacket.TYPE, CanvasUpdatePacket.STREAM_CODEC, CanvasUpdatePacket::handle);
        r.playToServer(CanvasBeginStrokePacket.TYPE, CanvasBeginStrokePacket.STREAM_CODEC, CanvasBeginStrokePacket::handle);
        r.playToServer(CanvasEndStrokePacket.TYPE, CanvasEndStrokePacket.STREAM_CODEC, CanvasEndStrokePacket::handle);
        r.playToServer(CanvasAppraisalPacket.TYPE, CanvasAppraisalPacket.STREAM_CODEC, CanvasAppraisalPacket::handle);
        r.playToServer(CanvasCloseConsumePacket.TYPE, CanvasCloseConsumePacket.STREAM_CODEC, CanvasCloseConsumePacket::handle);
        // server -> client
        r.playToClient(CanvasOpenPacket.TYPE, CanvasOpenPacket.STREAM_CODEC, ClientHandlers::handleOpenCanvas);
        r.playToClient(CanvasStrokeDeniedPacket.TYPE, CanvasStrokeDeniedPacket.STREAM_CODEC, ClientHandlers::handleDenied);
        // bidirectional: player strokes go to server, server broadcasts them back to nearby players
        r.playBidirectional(CanvasMiniUpdatePacket.TYPE, CanvasMiniUpdatePacket.STREAM_CODEC, CanvasMiniUpdatePacket::handle);
    }

    public static void sendToPlayer(CustomPacketPayload packet, ServerPlayer player) {
        player.connection.send(packet);
    }
}
