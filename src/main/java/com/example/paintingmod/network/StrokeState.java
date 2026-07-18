package com.example.paintingmod.network;

import com.example.paintingmod.canvas.CanvasBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side bookkeeping for the begin-stroke / end-stroke protocol.
 *
 * When a stroke begins we snapshot the canvas so that, if the player cannot pay for
 * the dyes at the end of the stroke, we can roll the pixels back. While a stroke is
 * active, only that player's pixel updates for that canvas are accepted (anti-cheat).
 */
public final class StrokeState {
    private StrokeState() {}

    public static final class Pending {
        public final BlockPos pos;
        public final int[] snapshot;
        public Pending(BlockPos pos, int[] snapshot) { this.pos = pos; this.snapshot = snapshot; }
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    public static void begin(ServerPlayer player, CanvasBlockEntity be) {
        PENDING.put(player.getUUID(), new Pending(be.getBlockPos(), be.getPixels().clone()));
    }

    public static Pending end(ServerPlayer player) {
        return PENDING.remove(player.getUUID());
    }

    public static boolean active(ServerPlayer player, BlockPos pos) {
        Pending p = PENDING.get(player.getUUID());
        return p != null && p.pos.equals(pos);
    }
}
