package com.example.paintingmod.canvas;

import com.example.paintingmod.client.DrawingScreen;
import com.example.paintingmod.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * The "magic canvas" (魔法画稿): a keepsake item produced by every magic appraisal. It
 * stores the painted artwork (in CUSTOM_DATA "Painting") plus the recognition label and the
 * mapped reward name, and lets the player reopen the drawing GUI to admire or re-edit it.
 */
public class MagicCanvasStubItem extends Item {
    public MagicCanvasStubItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) openStubGui(stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, lines, flag);
        if (ItemNBT.hasTag(stack, "Painting")) {
            PaintingData d = PaintingData.load(ItemNBT.getTag(stack, "Painting"));
            String label = ItemNBT.getString(stack, "Label");
            String reward = ItemNBT.getString(stack, "RewardName");
            lines.add(Component.literal("魔法鉴定画作").withStyle(ChatFormatting.ITALIC, ChatFormatting.LIGHT_PURPLE));
            if (!label.isBlank()) lines.add(Component.literal("§7鉴定：" + label));
            if (!reward.isBlank()) lines.add(Component.literal("§7对应：" + reward));
            lines.add(Component.literal("§7尺寸：" + d.width + "×" + d.height));
            lines.add(Component.literal("§8右键查看/再创作").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.literal("（空白画稿）").withStyle(ChatFormatting.GRAY));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void openStubGui(ItemStack stack) {
        PaintingData data;
        if (ItemNBT.hasTag(stack, "Painting")) {
            data = PaintingData.load(ItemNBT.getTag(stack, "Painting"));
        } else {
            int size = Math.max(16, Math.min(ModConfig.maxCanvasSize.get(), ModConfig.defaultCanvasSize.get()));
            data = new PaintingData(size, size);
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(new DrawingScreen(data, (w, h, px) -> {
            PaintingData saved = new PaintingData(w, h, px);
            ItemNBT.putTag(stack, "Painting", saved.save());
        }, null, false));
    }
}
