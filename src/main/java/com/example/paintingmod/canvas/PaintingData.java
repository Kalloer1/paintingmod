package com.example.paintingmod.canvas;

import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;

/**
 * A serializable block of RGB pixels. Used by both the handheld paint paper
 * (item NBT) and the wall-mounted paint paper (block entity). The backing
 * sentinel {@link Palette#BACKING} (-1) means transparent/erased.
 */
public final class PaintingData {
    public int width;
    public int height;
    public int[] pixels;
    public String author = "";
    public String title = "";
    public boolean redeemed = false;

    public PaintingData(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.pixels = new int[this.width * this.height];
        Arrays.fill(this.pixels, Palette.BACKING);
    }

    public PaintingData(int width, int height, int[] pixels) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        int need = this.width * this.height;
        if (pixels != null && pixels.length == need) {
            this.pixels = pixels.clone();
        } else {
            this.pixels = new int[need];
            Arrays.fill(this.pixels, Palette.BACKING);
        }
    }

    public PaintingData copy() {
        PaintingData d = new PaintingData(width, height, pixels);
        d.author = author;
        d.title = title;
        d.redeemed = redeemed;
        return d;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        tag.putIntArray("Pixels", pixels);
        tag.putString("Author", author);
        tag.putString("Title", title);
        tag.putBoolean("Redeemed", redeemed);
        return tag;
    }

    public static PaintingData load(CompoundTag tag) {
        int w = Math.max(1, tag.getInt("Width"));
        int h = Math.max(1, tag.getInt("Height"));
        int[] px = tag.getIntArray("Pixels");
        if (px.length != w * h) {
            px = new int[w * h];
            Arrays.fill(px, Palette.BACKING);
        }
        PaintingData d = new PaintingData(w, h, px);
        d.author = tag.getString("Author");
        d.title = tag.getString("Title");
        d.redeemed = tag.getBoolean("Redeemed");
        return d;
    }

    public void setPixel(int index, int value) {
        if (index < 0 || index >= pixels.length) return;
        pixels[index] = value;
    }

    public int getPixel(int index) {
        if (index < 0 || index >= pixels.length) return Palette.BACKING;
        return pixels[index];
    }

    public void resampleTo(int nw, int nh) {
        nw = Math.max(1, nw);
        nh = Math.max(1, nh);
        int[] next = new int[nw * nh];
        int ow = width, oh = height;
        for (int y = 0; y < nh; y++) {
            int sy = (oh <= 1) ? 0 : (y * oh / nh);
            for (int x = 0; x < nw; x++) {
                int sx = (ow <= 1) ? 0 : (x * ow / nw);
                next[y * nw + x] = pixels[sy * ow + sx];
            }
        }
        width = nw;
        height = nh;
        pixels = next;
    }

    public int area() { return width * height; }
}
