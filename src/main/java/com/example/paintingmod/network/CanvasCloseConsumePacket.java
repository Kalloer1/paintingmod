package com.example.paintingmod.network;

import com.example.paintingmod.canvas.Palette;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Client -> Server, sent once when the drawing GUI closes: lists every distinct dye
 * colour the player actually painted with this session. The server charges exactly one
 * of each (best-effort — if the player no longer carries it, it is skipped) so the cost
 * of painting is "1 dye per colour used", not per stroke.
 */
public record CanvasCloseConsumePacket(Set<Integer> dyeIndices) implements CustomPacketPayload {

    public static final Type<CanvasCloseConsumePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("paintingmod", "canvas_close_consume"));

    public static final StreamCodec<ByteBuf, CanvasCloseConsumePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = (FriendlyByteBuf) buf;
                int[] arr = new int[p.dyeIndices.size()];
                int i = 0;
                for (int v : p.dyeIndices) arr[i++] = v;
                f.writeVarIntArray(arr);
            },
            buf -> {
                FriendlyByteBuf f = (FriendlyByteBuf) buf;
                int[] arr = f.readVarIntArray();
                Set<Integer> set = new HashSet<>();
                for (int v : arr) set.add(v);
                return new CanvasCloseConsumePacket(set);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CanvasCloseConsumePacket p, IPayloadContext ctx) {
        if (!ctx.flow().isServerbound()) return;
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;
            for (int idx : p.dyeIndices()) {
                Item dye = Palette.dyeItem(idx);
                if (dye == null) continue;
                shrink(player, dye, 1);
            }
        });
    }

    private static void shrink(ServerPlayer player, Item item, int n) {
        int left = n;
        for (ItemStack s : player.getInventory().items) {
            if (left <= 0) break;
            if (s.is(item)) { int take = Math.min(left, s.getCount()); s.shrink(take); left -= take; }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (left <= 0) break;
            if (s.is(item)) { int take = Math.min(left, s.getCount()); s.shrink(take); left -= take; }
        }
    }
}
