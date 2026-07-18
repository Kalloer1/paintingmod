package com.example.paintingmod.canvas;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Describes what pigment a stroke was painted with, so the server can charge the
 * right dyes. Travels inside {@code CanvasEndStrokePacket}.
 *
 *   NONE     - eraser / free (no charge)
 *   DYE      - a single base dye (indices = [i], weights = [1])
 *   MIX      - several dyes blended (indices + parallel weights)
 *   UNIVERSAL- the universal dye, any RGB (no index needed)
 */
public final class PaintSource {
    public static final byte NONE = 0, DYE = 1, MIX = 2, UNIVERSAL = 3;

    public final byte type;
    public final int[] indices;  // dye indices (DyeColor ordinal)
    public final int[] weights;  // parallel to indices

    public PaintSource(byte type, int[] indices, int[] weights) {
        this.type = type;
        this.indices = indices != null ? indices : new int[0];
        this.weights = weights != null ? weights : new int[0];
    }

    public static PaintSource none() { return new PaintSource(NONE, null, null); }
    public static PaintSource dye(int i) { return new PaintSource(DYE, new int[]{i}, new int[]{1}); }
    public static PaintSource mix(int[] idx, int[] w) { return new PaintSource(MIX, idx, w); }
    public static PaintSource universal() { return new PaintSource(UNIVERSAL, null, null); }

    public static final StreamCodec<ByteBuf, PaintSource> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = (FriendlyByteBuf) buf;
                f.writeByte(p.type);
                f.writeVarIntArray(p.indices);
                f.writeVarIntArray(p.weights);
            },
            buf -> {
                FriendlyByteBuf f = (FriendlyByteBuf) buf;
                byte t = f.readByte();
                int[] i = f.readVarIntArray();
                int[] w = f.readVarIntArray();
                return new PaintSource(t, i, w);
            });
}
