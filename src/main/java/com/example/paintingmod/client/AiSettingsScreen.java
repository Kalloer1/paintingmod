package com.example.paintingmod.client;

import com.example.paintingmod.ai.AiConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class AiSettingsScreen extends Screen {
    private final Screen parent;
    private EditBox baseBox, tokenBox, modelBox, tempBox, tokBox;
    private Button thinkBox, showThinkBox;

    public AiSettingsScreen(Screen parent) {
        super(Component.translatable("screen.paintingmod.ai_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int w = 320, h = 18;
        AiConfig cfg = AiConfig.get();

        addLabel(cx, 44, "API 地址");
        baseBox = new EditBox(font, cx - w / 2, 60, w, h, Component.literal(""));
        baseBox.setMaxLength(200);
        baseBox.setValue(cfg.apiBase);
        this.addRenderableWidget(baseBox);

        addLabel(cx, 92, "API Token（仅保存在本地）");
        tokenBox = new EditBox(font, cx - w / 2, 108, w, h, Component.literal(""));
        tokenBox.setMaxLength(400);
        tokenBox.setValue(cfg.apiToken);
        this.addRenderableWidget(tokenBox);

        addLabel(cx, 140, "视觉模型（如 glm-4.6v-flashx / Qwen/...）");
        modelBox = new EditBox(font, cx - w / 2, 156, w, h, Component.literal(""));
        modelBox.setMaxLength(120);
        modelBox.setValue(cfg.model);
        this.addRenderableWidget(modelBox);

        // 深度思考开关（GLM 系生效）—— 用可切换按钮实现，避免 Checkbox 构造签名差异
        thinkBox = Button.builder(Component.literal(thinkLabel(cfg.think)), b -> {
                    cfg.think = !cfg.think;
                    b.setMessage(Component.literal(thinkLabel(cfg.think)));
                })
                .pos(cx - w / 2, 182)
                .size(w, 16)
                .build();
        this.addRenderableWidget(thinkBox);

        // 显示思考过程开关（与「深度思考」解耦：可只让模型后台思考、不在游戏内显示）
        showThinkBox = Button.builder(Component.literal(showThinkLabel(cfg.showThinking)), b -> {
                    cfg.showThinking = !cfg.showThinking;
                    b.setMessage(Component.literal(showThinkLabel(cfg.showThinking)));
                })
                .pos(cx - w / 2, 202)
                .size(w, 16)
                .build();
        this.addRenderableWidget(showThinkBox);

        // 温度 / 最大 token 同排
        addLabel(cx - w / 2 + 4, 204, "温度 temperature (0~1)");
        tempBox = new EditBox(font, cx - w / 2, 220, 150, h, Component.literal(""));
        tempBox.setMaxLength(6);
        tempBox.setValue(String.valueOf(cfg.temperature));
        this.addRenderableWidget(tempBox);

        addLabel(cx + 10 + 4, 204, "最大 Token max_tokens");
        tokBox = new EditBox(font, cx + 10, 220, 150, h, Component.literal(""));
        tokBox.setMaxLength(6);
        tokBox.setValue(String.valueOf(cfg.maxTokens));
        this.addRenderableWidget(tokBox);

        addLabel(cx, 252, "智谱示例 base: https://open.bigmodel.cn/api/paas/v4   model: glm-4.6v-flashx");

        // 保存 / 返回 按钮
        this.addRenderableWidget(makeButton(cx - 150, 276, 140, 22, "保存并关闭", () -> {
            AiConfig c = AiConfig.get();
            if (!baseBox.getValue().isBlank()) c.apiBase = baseBox.getValue().trim();
            c.apiToken = tokenBox.getValue().trim();
            if (!modelBox.getValue().isBlank()) c.model = modelBox.getValue().trim();
            c.think = cfg.think;
            c.showThinking = cfg.showThinking;
            try {
                double t = Double.parseDouble(tempBox.getValue().trim());
                c.temperature = Math.max(0.0, Math.min(1.0, t));
            } catch (NumberFormatException ignored) {}
            try {
                int mt = Integer.parseInt(tokBox.getValue().trim());
                c.maxTokens = Math.max(200, Math.min(4096, mt));
            } catch (NumberFormatException ignored) {}
            AiConfig.save();
            this.minecraft.setScreen(parent);
        }));
        this.addRenderableWidget(makeButton(cx + 10, 276, 140, 22, "取消", () -> this.minecraft.setScreen(parent)));
    }

    private void addLabel(int cx, int y, String text) {
        this.addRenderableWidget(new net.minecraft.client.gui.components.AbstractWidget(cx - 160, y, 320, 12, Component.literal(text)) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                g.drawCenteredString(font, this.getMessage(), this.getX() + 160, this.getY(), 0xCCCCCC);
            }
            @Override
            protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput o) {}
        });
    }

    private net.minecraft.client.gui.components.Button makeButton(int x, int y, int w, int h, String label, Runnable action) {
        return net.minecraft.client.gui.components.Button.builder(Component.literal(label), b -> action.run())
                .pos(x, y).size(w, h).build();
    }

    private static String thinkLabel(boolean on) {
        return on ? "§d深度思考：§a开（模型侧推理）" : "§d深度思考：§c关";
    }

    private static String showThinkLabel(boolean on) {
        return on ? "§d显示思考过程：§a开（界面+聊天栏可见）" : "§d显示思考过程：§c关";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, this.width, this.height, 0xDD121216);
        g.drawCenteredString(font, title, this.width / 2, 16, 0xFFFFFF);
    }
}
