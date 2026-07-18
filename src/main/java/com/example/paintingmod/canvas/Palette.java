package com.example.paintingmod.canvas;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

/**
 * Color & dye helpers. Pixels are stored as raw 0xRRGGBB; the sentinel {@link #BACKING}
 * (-1) means "transparent / erased" so the wooden frame shows through.
 *
 * Also exposes the 16 vanilla dye colors/items and a subtractive-ish mixing function
 * used by the in-GUI color mixer.
 */
public final class Palette {
    /** Transparent / eraser sentinel. */
    public static final int BACKING = -1;
    /** Color drawn behind transparent pixels inside the GUI (paper). */
    public static final int PAPER = 0xE8E0CE;

    private Palette() {}

    private static final int[] DYE_RGB = new int[16];
    private static final Item[] DYE_ITEMS = new Item[16];
    static {
        DyeColor[] dc = DyeColor.values();
        for (int i = 0; i < 16 && i < dc.length; i++) {
            DYE_RGB[i] = dc[i].getTextColor();
            DYE_ITEMS[i] = BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:" + dc[i].getName() + "_dye"));
        }
    }

    /** RGB of a dye by DyeColor ordinal (0..15). */
    public static int dyeRgb(int index) {
        if (index < 0 || index >= 16) return PAPER;
        return DYE_RGB[index];
    }

    /** The Item for a dye by DyeColor ordinal (0..15). */
    public static Item dyeItem(int index) {
        if (index < 0 || index >= 16) return null;
        return DYE_ITEMS[index];
    }

    /**
     * Mix dye colors (indices into the 16 dyes) with the given integer weights.
     * Uses an averaged, slightly darkened result so combining paints feels like paint.
     */
    public static int mix(int[] indices, int[] weights) {
        if (indices == null || indices.length == 0) return PAPER;
        double r = 0, g = 0, b = 0, wsum = 0;
        for (int i = 0; i < indices.length; i++) {
            int rgb = dyeRgb(indices[i]);
            int w = (weights != null && i < weights.length) ? Math.max(1, weights[i]) : 1;
            r += ((rgb >> 16) & 0xFF) * w;
            g += ((rgb >> 8) & 0xFF) * w;
            b += (rgb & 0xFF) * w;
            wsum += w;
        }
        r /= wsum; g /= wsum; b /= wsum;
        double f = 0.85; // darken a touch so mixes read as blended paint
        return (clamp((int) (r * f)) << 16) | (clamp((int) (g * f)) << 8) | clamp((int) (b * f));
    }

    public static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
