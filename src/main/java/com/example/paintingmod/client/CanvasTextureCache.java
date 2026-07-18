package com.example.paintingmod.client;

import com.example.paintingmod.PixelCanvas;
import com.example.paintingmod.canvas.CanvasBlockEntity;
import com.example.paintingmod.canvas.Palette;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/** Client-only cache of the DynamicTexture used to render each canvas block.
 *  Re-encodes only the pixels that actually changed since the last upload, so a
 *  128x128 canvas no longer re-runs ~16k JNI pixel writes on every version bump. */
@OnlyIn(Dist.CLIENT)
public final class CanvasTextureCache {
    private static final Map<BlockPos, Holder> CACHE = new HashMap<>();

    private CanvasTextureCache() {}

    public static ResourceLocation getOrBuild(CanvasBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        int w = be.getWidth(), h = be.getHeight();
        int rv = be.getRenderVersion();
        Holder holder = CACHE.get(pos);
        if (holder == null || holder.w != w || holder.h != h) {
            if (holder != null) Minecraft.getInstance().getTextureManager().release(holder.rl);
            NativeImage img = new NativeImage(w, h, true);
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(PixelCanvas.MOD_ID,
                    "canvas/" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ());
            Minecraft.getInstance().getTextureManager().register(rl, tex);
            holder = new Holder(tex, img, rl, w, h, rv);
            holder.prev = be.getPixels().clone();
            encodeAll(holder, holder.prev);
            holder.tex.upload();
            CACHE.put(pos, holder);
            return holder.rl;
        }
        if (holder.renderVersion != rv) {
            holder.renderVersion = rv;
            int[] cur = be.getPixels();
            int[] prev = holder.prev;
            int minX = w, maxX = -1, minY = h, maxY = -1;
            int n = Math.min(cur.length, prev.length);
            for (int i = 0; i < n; i++) {
                if (cur[i] != prev[i]) {
                    int x = i % w, y = i / w;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
            if (maxX >= 0) {
                NativeImage img = holder.img;
                for (int y = minY; y <= maxY; y++)
                    for (int x = minX; x <= maxX; x++)
                        encodePixel(img, x, y, w, h, cur[y * w + x]);
                holder.tex.upload();
            }
            holder.prev = cur.clone();
        }
        return holder.rl;
    }

    public static void invalidate(BlockPos pos) {
        Holder holder = CACHE.remove(pos);
        if (holder != null) Minecraft.getInstance().getTextureManager().release(holder.rl);
    }

    private static void encodePixel(NativeImage img, int x, int y, int w, int h, int v) {
        int r, g, b, a;
        if (v < 0) { r = g = b = 0; a = 0; }
        else { r = (v >> 16) & 0xFF; g = (v >> 8) & 0xFF; b = v & 0xFF; a = 0xFF; }
        // data row 0 = top; BER maps texture v=0 (row 0) to the top of the paper (no flip)
        img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
    }

    private static void encodeAll(Holder holder, int[] px) {
        NativeImage img = holder.img;
        for (int y = 0; y < holder.h; y++)
            for (int x = 0; x < holder.w; x++)
                encodePixel(img, x, y, holder.w, holder.h, px[y * holder.w + x]);
    }

    private static final class Holder {
        final DynamicTexture tex;
        final NativeImage img;
        final ResourceLocation rl;
        final int w, h;
        int renderVersion;
        int[] prev;

        Holder(DynamicTexture tex, NativeImage img, ResourceLocation rl, int w, int h, int rv) {
            this.tex = tex;
            this.img = img;
            this.rl = rl;
            this.w = w;
            this.h = h;
            this.renderVersion = rv;
        }
    }
}
