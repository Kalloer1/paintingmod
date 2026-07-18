package com.example.paintingmod.client;

import com.example.paintingmod.canvas.PaintingData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CanvasSizeScreen extends Screen {
    public interface ConfirmCallback {
        void confirm(int width, int height);
    }

    private final PaintingData data;
    private final ConfirmCallback callback;
    private final List<Rect> buttons = new ArrayList<>();
    private record Rect(int x, int y, int w, int h, Component label, Runnable action) {
        boolean hit(int mx, int my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    private int targetW, targetH;

    public CanvasSizeScreen(PaintingData data, ConfirmCallback callback) {
        super(Component.translatable("screen.paintingmod.size"));
        this.data = data;
        this.targetW = data.width;
        this.targetH = data.height;
        this.callback = callback;
    }

    private void computeLayout() {
        buttons.clear();
        int cx = this.width / 2, y = 60, bw = 96, bh = 20, gap = 4;
        addBtn(cx - bw - gap, y, bw, bh, Component.literal("64×64"), () -> { targetW = 64; targetH = 64; });
        addBtn(cx, y, bw, bh, Component.literal("128×128"), () -> { targetW = 128; targetH = 128; });
        addBtn(cx + bw + gap, y, bw, bh, Component.literal("原尺寸"), () -> { targetW = data.width; targetH = data.height; });
        y += bh + 20;
        addBtn(cx - 100, y, 96, bh, Component.literal("W-"), () -> targetW = Math.max(16, targetW / 2));
        addBtn(cx + 4, y, 96, bh, Component.literal("W+"), () -> targetW = Math.min(128, targetW * 2));
        y += bh + 10;
        addBtn(cx - 100, y, 96, bh, Component.literal("H-"), () -> targetH = Math.max(16, targetH / 2));
        addBtn(cx + 4, y, 96, bh, Component.literal("H+"), () -> targetH = Math.min(128, targetH * 2));
        y += bh + 30;
        addBtn(cx - 60, y, 120, bh, Component.literal("确认调整"), () -> {
            if (callback != null) callback.confirm(targetW, targetH);
        });
    }

    private void addBtn(int x, int y, int w, int h, Component label, Runnable action) {
        buttons.add(new Rect(x, y, w, h, label, action));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        computeLayout();
        int mx = (int) mouseX, my = (int) mouseY;
        for (Rect b : buttons) if (b.hit(mx, my)) { b.action().run(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        computeLayout();
        g.fill(0, 0, this.width, this.height, 0xDD121216);
        g.drawCenteredString(font, title, this.width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal("当前: " + data.width + "x" + data.height + "  目标: " + targetW + "x" + targetH),
                this.width / 2, 36, 0xCCCCCC);
        for (Rect b : buttons) {
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0xFF2C2C30);
            g.drawCenteredString(font, b.label(), b.x() + b.w() / 2, b.y() + 5, 0xFFFFFF);
        }
    }
}
