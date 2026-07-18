package com.example.paintingmod.canvas;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Helpers for reading/writing custom NBT on ItemStacks in Minecraft 1.21. */
public final class ItemNBT {
    private ItemNBT() {}

    public static boolean hasTag(ItemStack stack, String key) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.contains(key);
    }

    public static CompoundTag getTag(ItemStack stack, String key) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return new CompoundTag();
        CompoundTag tag = cd.copyTag();
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND) ? tag.getCompound(key) : new CompoundTag();
    }

    public static void putTag(ItemStack stack, String key, CompoundTag value) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = cd != null ? cd.copyTag() : new CompoundTag();
        tag.put(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Read a top-level string from the item's CUSTOM_DATA (not nested under another key). */
    public static boolean hasTopKey(ItemStack stack, String key) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.contains(key);
    }

    public static String getString(ItemStack stack, String key) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return "";
        CompoundTag tag = cd.copyTag();
        return tag.contains(key, net.minecraft.nbt.Tag.TAG_STRING) ? tag.getString(key) : "";
    }
}
