package com.example.paintingmod.canvas;

import com.example.paintingmod.config.ModConfig;
import com.example.paintingmod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores the painting as a {@link PaintingData}. Resolution is configurable;
 * new paint papers are sized from config at placement ({@link #initSize}), and the
 * in-GUI size picker can resample an existing canvas ({@link #resampleTo}).
 */
public class CanvasBlockEntity extends BlockEntity {
    private final PaintingData data;
    private int renderVersion = 0;

    public CanvasBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, 16, 16);
    }

    public CanvasBlockEntity(BlockPos pos, BlockState state, int width, int height) {
        super(ModBlocks.CANVAS_BE.get(), pos, state);
        this.data = new PaintingData(width, height);
    }

    public PaintingData getData() { return data; }
    public int getWidth() { return data.width; }
    public int getHeight() { return data.height; }
    public int[] getPixels() { return data.pixels; }
    public int getRenderVersion() { return renderVersion; }

    public void setPixel(int index, int value) {
        if (index < 0 || index >= data.area()) return;
        data.pixels[index] = value;
    }

    public void setAuthor(String author) { data.author = author; }

    public void setPixels(int[] p) {
        data.pixels = p != null && p.length == data.area() ? p.clone() : new int[data.area()];
    }

    public void setFrom(PaintingData src) {
        data.width = src.width;
        data.height = src.height;
        data.pixels = src.pixels.clone();
        data.author = src.author;
        data.title = src.title;
        data.redeemed = src.redeemed;
        renderVersion++;
    }

    public void initSize(int w, int h) {
        data.width = Math.max(1, w);
        data.height = Math.max(1, h);
        data.pixels = new int[data.area()];
        java.util.Arrays.fill(data.pixels, Palette.BACKING);
        setChanged();
    }

    public void resampleTo(int nw, int nh) {
        data.resampleTo(nw, nh);
        renderVersion++;
        markUpdated();
    }

    public void markUpdated() {
        renderVersion++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /** Wipe the canvas back to blank backing pixels (used after a magic appraisal clears it). */
    public void clearPixels() {
        java.util.Arrays.fill(data.pixels, Palette.BACKING);
        renderVersion++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /** Bump the render version so clients re-upload the texture, WITHOUT triggering a
     *  full block-entity network sync. Used for live stroke pixels so we don't serialize
     *  and broadcast the whole canvas every 50ms during drawing. */
    public void bumpRenderVersion() {
        renderVersion++;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.merge(data.save());
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        PaintingData loaded = PaintingData.load(tag);
        data.width = loaded.width;
        data.height = loaded.height;
        data.pixels = loaded.pixels;
        data.author = loaded.author;
        data.title = loaded.title;
        data.redeemed = loaded.redeemed;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
        return data.save();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        PaintingData loaded = PaintingData.load(tag);
        data.width = loaded.width;
        data.height = loaded.height;
        data.pixels = loaded.pixels;
        renderVersion++;
    }
}
