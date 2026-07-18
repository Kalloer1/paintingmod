package com.example.paintingmod.client;

import com.example.paintingmod.ai.AiConfig;
import com.example.paintingmod.ai.RewardTable;
import com.example.paintingmod.canvas.PaintSource;
import com.example.paintingmod.canvas.PaintingData;
import com.example.paintingmod.canvas.Palette;
import com.example.paintingmod.config.ModConfig;
import com.example.paintingmod.network.CanvasAppraisalPacket;
import com.example.paintingmod.network.CanvasBeginStrokePacket;
import com.example.paintingmod.network.CanvasCloseConsumePacket;
import com.example.paintingmod.network.CanvasEndStrokePacket;
import com.example.paintingmod.network.CanvasMiniUpdatePacket;
import com.example.paintingmod.network.CanvasUpdatePacket;
import com.example.paintingmod.registry.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

@OnlyIn(Dist.CLIENT)
public class DrawingScreen extends Screen {
    public interface SaveCallback {
        void save(int width, int height, int[] pixels);
    }

    private enum Tool { BRUSH, ERASER, EYEDROPPER, FILL }

    private final PaintingData data;
    private final SaveCallback onSave;
    private final @Nullable BlockPos pos; // null = editing a handheld item
    private final boolean allowAppraisal; // false for saved magic canvases (no re-appraisal, prevents item dupe)

    private int currentColor = Palette.dyeRgb(14); // default red
    private PaintSource currentSource = PaintSource.dye(14);
    private Tool tool = Tool.BRUSH;
    private int brushSize = 2;

    private final List<PaintingData> undoStack = new ArrayList<>();
    private final List<PaintingData> redoStack = new ArrayList<>();

    private boolean painting = false;
    private boolean dirty = false;
    private final List<Integer> pendIdx = new ArrayList<>();
    private final List<Integer> pendVal = new ArrayList<>();
    private long lastFlush = 0;

    // mixer
    private final Set<Integer> ownedDyes = new HashSet<>();
    // dyes actually used during this session — consumed (1 each) when the GUI closes
    private final Set<Integer> usedDyes = new HashSet<>();

    // universal dye
    private int ur = 255, ug = 0, ub = 0;
    private boolean universalAvailable = false;

    // AI recognition
    private String aiResult = "";
    /** AI 的推理过程（来自 GLM reasoning_content 或提示词里的「思考过程」行），展示给玩家。 */
    private String aiThinking = "";
    private boolean aiBusy = false;

    // layout
    private int canvasX, canvasY, canvasDispW, canvasDispH;
    private int leftX, leftW, rightX, rightW;
    private final List<Rect> buttons = new ArrayList<>();
    private final List<DyeChip> dyeChips = new ArrayList<>();
    private final List<int[]> dividers = new ArrayList<>(); // {x1,y,x2} thin separators
    private record Rect(int x, int y, int w, int h, Component label, Runnable action, BooleanSupplier active) {
        boolean hit(int mx, int my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }
    private record DyeChip(int x, int y, int w, int h, int index) {
        boolean hit(int mx, int my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    // brush size slider (1..16)
    private int brushSliderX = 0, brushSliderY = 0, brushSliderW = 0;
    private boolean draggingBrush = false;
    private static final int BRUSH_MIN = 1, BRUSH_MAX = 16;

    // universal dye RGB sliders (horizontal, with numeric readout)
    private int sliderW = 116;
    private static final int SLIDER_H = 12;
    private final int[] sliderX = new int[3];
    private final int[] sliderY = new int[3];
    private int draggingSlider = -1; // 0=R,1=G,2=B
    // stored left-panel y positions so render and layout never drift
    private int paletteY = 0;
    private int infoY = 0;
    private int sliderHeaderY = 0;
    private int previewY = 0;

    // cached texture for the GUI canvas
    private DynamicTexture dynTex;
    private ResourceLocation dynRl;
    private boolean texDirty = true;
    // dirty-rectangle tracking so we only re-encode the pixels that actually changed
    private int dMinX = 0, dMinY = 0, dMaxX = -1, dMaxY = -1;
    private boolean dFull = false;

    public DrawingScreen(PaintingData initial, SaveCallback onSave, @Nullable BlockPos pos, boolean allowAppraisal) {
        super(Component.translatable("screen.paintingmod.title"));
        this.data = initial.copy();
        this.onSave = onSave;
        this.pos = pos;
        this.allowAppraisal = allowAppraisal;
    }

    public PaintingData getData() { return data; }
    public @Nullable BlockPos getPos() { return pos; }

    public void reloadPixels(int[] p) {
        if (p != null && p.length == data.area()) {
            System.arraycopy(p, 0, data.pixels, 0, p.length);
            texDirty = true;
            dFull = true;
            dirty = false;
        }
    }

    @Override
    public void onClose() {
        save();
        // consume one of each dye colour actually used during this session
        if (!usedDyes.isEmpty())
            ClientHandlers.sendToServer(new CanvasCloseConsumePacket(usedDyes));
        releaseTexture();
        super.onClose();
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        releaseTexture();
        super.resize(mc, w, h);
    }

    @Override
    public void init() {
        super.init();
        computeLayout();
    }

    private void releaseTexture() {
        if (dynRl != null) {
            Minecraft.getInstance().getTextureManager().release(dynRl);
            dynRl = null; dynTex = null;
        }
    }

    // ---- layout ----

    private void computeLayout() {
        buttons.clear();
        dyeChips.clear();
        dividers.clear();

        universalAvailable = ModConfig.universalDyeEnabled.get() && hasUniversal();

        // which dyes does the player actually carry? gate the palette on this
        ownedDyes.clear();
        var pl0 = Minecraft.getInstance().player;
        if (pl0 != null) {
            for (int i = 0; i < 16; i++) {
                var it = Palette.dyeItem(i);
                if (it != null && playerHas(it)) ownedDyes.add(i);
            }
        }

        // Panel geometry
        leftX = 6; leftW = 132;
        rightW = 104; rightX = this.width - rightW - 6;

        int topBar = 42;
        int bottomInfoH = 56;           // dedicated bottom strip for AI / status
        int usableTop = topBar + 6;
        int usableBottom = this.height - bottomInfoH - 6;
        int usableH = Math.max(80, usableBottom - usableTop);

        int zoneX0 = leftX + leftW + 10;
        int zoneX1 = rightX - 10;
        int zoneW = Math.max(80, zoneX1 - zoneX0);

        int maxDisp = Math.min(zoneW, usableH);
        int maxDim = Math.max(data.width, data.height);
        canvasDispW = maxDisp * data.width / maxDim;
        canvasDispH = maxDisp * data.height / maxDim;
        canvasX = zoneX0 + (zoneW - canvasDispW) / 2;
        canvasY = usableTop + (usableH - canvasDispH) / 2;

        // ---- right toolbar: grouped with gaps + dividers ----
        int bx = rightX + 4, bw = rightW - 8, bh = 18, gap = 2, grp = 8;
        int by = usableTop + 12; // leave room for a small "工具" header
        // group 1: draw
        by = addBtn(bx, by, bw, bh, Component.literal("画笔"), () -> tool = Tool.BRUSH, () -> tool == Tool.BRUSH) + gap;
        by = addBtn(bx, by, bw, bh, Component.literal("橡皮"), () -> tool = Tool.ERASER, () -> tool == Tool.ERASER) + gap;
        by = addBtn(bx, by, bw, bh, Component.literal("取色"), () -> tool = Tool.EYEDROPPER, () -> tool == Tool.EYEDROPPER) + gap;
        by = addBtn(bx, by, bw, bh, Component.literal("填充"), () -> tool = Tool.FILL, () -> tool == Tool.FILL);
        dividers.add(new int[]{bx, by + grp / 2, bx + bw}); by += grp;
        // group 2: edit
        by = addBtn(bx, by, bw, bh, Component.literal("撤销"), this::undo, () -> false) + gap;
        by = addBtn(bx, by, bw, bh, Component.literal("重做"), this::redo, () -> false) + gap;
        by = addBtn(bx, by, bw, bh, Component.literal("清空"), this::clearAll, () -> false);
        dividers.add(new int[]{bx, by + grp / 2, bx + bw}); by += grp;
        // group 3: file
        by = addBtn(bx, by, bw, bh, Component.literal("保存"), this::save, () -> false) + gap;
        by = addBtn(bx, by, bw, bh, Component.literal("调整尺寸"), this::openSize, () -> false);
        if (allowAppraisal) {
            dividers.add(new int[]{bx, by + grp / 2, bx + bw}); by += grp;
            // group 4: magic (only on a real canvas, never on a saved magic-canvas keepsake)
            by = addBtn(bx, by, bw, bh, Component.literal("§d魔法鉴定"), this::doAiRecognize, () -> false) + gap;
            by = addBtn(bx, by, bw, bh, Component.literal("§d魔法书"), this::openAiSettings, () -> false);
        }
        dividers.add(new int[]{bx, by + grp / 2, bx + bw}); by += grp;
        // close
        addBtn(bx, by, bw, bh, Component.literal("关闭"), this::onClose, () -> false);

        // ---- left panel: palette + mixer + current color + RGB ----
        int lx = leftX + 8;
        int ly = usableTop + 12; // room for "颜料" header

        paletteY = ly;
        int cs = 28, gp = 2, cols = 4;
        for (int i = 0; i < 16; i++) {
            int col = i % cols, row = i / cols;
            dyeChips.add(new DyeChip(lx + col * (cs + gp), ly + row * (cs + gp), cs, cs, i));
        }
        ly += 4 * (cs + gp) + 6;

        dividers.add(new int[]{lx, ly + 2, lx + leftW - 16}); ly += 8;

        infoY = ly;
        ly += 32;

        // brush size slider (always available)
        brushSliderY = ly + 12;
        brushSliderX = lx;
        brushSliderW = leftW - 16;
        ly += SLIDER_H + 20;

        if (universalAvailable) {
            dividers.add(new int[]{lx, ly - 4, lx + leftW - 16});
            sliderHeaderY = ly;
            ly += 12;
            sliderW = leftW - 16;
            sliderX[0] = sliderX[1] = sliderX[2] = lx;
            sliderY[0] = ly;
            sliderY[1] = ly + (SLIDER_H + 6);
            sliderY[2] = ly + (SLIDER_H + 6) * 2;
            ly += (SLIDER_H + 6) * 3 + 4;
            previewY = ly;
            ly += 30;
            addBtn(lx, ly, leftW - 16, bh, Component.literal("§d蘸取万能色"), this::useUniversal, () -> false);
        }
    }

    /** adds a button and returns its bottom y */
    private int addBtn(int x, int y, int w, int h, Component label, Runnable action, BooleanSupplier active) {
        buttons.add(new Rect(x, y, w, h, label, action, active));
        return y + h;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static String truncateThinking(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        if (s.length() <= 200) return s;
        return s.substring(0, 200) + "…";
    }

    private boolean playerHas(Item item) {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return true;
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) return true;
        return false;
    }

    private boolean hasUniversal() {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.UNIVERSAL_DYE.get())) return true;
        for (ItemStack s : p.getInventory().offhand) if (s.is(ModItems.UNIVERSAL_DYE.get())) return true;
        return false;
    }

    /** A vanilla dye chip is selectable if the player owns that dye, or holds the universal dye (master key). */
    private boolean chipUnlocked(int i) {
        return ownedDyes.contains(i) || universalAvailable;
    }

    // ---- painting ----

    private void setPixelIfChanged(int idx, int value) {
        if (idx < 0 || idx >= data.area()) return;
        if (data.pixels[idx] != value) {
            data.pixels[idx] = value;
            pendIdx.add(idx);
            pendVal.add(value);
            texDirty = true;
            dirty = true;
            markDirty(idx % data.width, idx / data.width);
        }
    }

    private void markDirty(int x, int y) {
        if (dFull) return;
        if (dMaxX < 0) { dMinX = dMaxX = x; dMinY = dMaxY = y; }
        else {
            if (x < dMinX) dMinX = x;
            if (x > dMaxX) dMaxX = x;
            if (y < dMinY) dMinY = y;
            if (y > dMaxY) dMaxY = y;
        }
    }

    private void paintBrush(int cx, int cy) {
        int r = brushSize / 2;
        int value = (tool == Tool.ERASER) ? Palette.BACKING : currentColor;
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < data.width && y >= 0 && y < data.height) setPixelIfChanged(y * data.width + x, value);
            }
    }

    private void fill(int cx, int cy) {
        int target = data.pixels[cy * data.width + cx];
        int repl = (tool == Tool.ERASER) ? Palette.BACKING : currentColor;
        if (target == repl) return;
        boolean[] visited = new boolean[data.area()];
        int[] stack = new int[data.area()];
        int sp = 0;
        int start = cy * data.width + cx;
        stack[sp++] = start;
        visited[start] = true;
        while (sp > 0) {
            int idx = stack[--sp];
            if (data.pixels[idx] != target) continue;
            setPixelIfChanged(idx, repl);
            int x = idx % data.width, y = idx / data.width;
            if (x > 0 && !visited[idx - 1]) { visited[idx - 1] = true; stack[sp++] = idx - 1; }
            if (x < data.width - 1 && !visited[idx + 1]) { visited[idx + 1] = true; stack[sp++] = idx + 1; }
            if (y > 0 && !visited[idx - data.width]) { visited[idx - data.width] = true; stack[sp++] = idx - data.width; }
            if (y < data.height - 1 && !visited[idx + data.width]) { visited[idx + data.width] = true; stack[sp++] = idx + data.width; }
        }
    }

    private boolean tryPaint(int mx, int my) {
        if (mx < canvasX || my < canvasY || mx >= canvasX + canvasDispW || my >= canvasY + canvasDispH) return false;
        int cx = (mx - canvasX) * data.width / canvasDispW;
        int cy = (my - canvasY) * data.height / canvasDispH;
        if (cx < 0 || cx >= data.width || cy < 0 || cy >= data.height) return false;
        if (tool == Tool.EYEDROPPER) {
            int c = data.pixels[cy * data.width + cx];
            if (c >= 0) { currentColor = c; currentSource = PaintSource.none(); }
            return true;
        }
        if (tool == Tool.FILL) { fill(cx, cy); return true; }
        paintBrush(cx, cy);
        return true;
    }

    private void pushUndo() {
        undoStack.add(data.copy());
        if (undoStack.size() > 64) undoStack.remove(0);
        redoStack.clear();
    }
    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.add(data.copy());
        PaintingData prev = undoStack.remove(undoStack.size() - 1);
        data.width = prev.width; data.height = prev.height;
        data.pixels = prev.pixels;
        texDirty = true; dFull = true; dirty = true; save();
    }
    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.add(data.copy());
        PaintingData next = redoStack.remove(redoStack.size() - 1);
        data.width = next.width; data.height = next.height;
        data.pixels = next.pixels;
        texDirty = true; dFull = true; dirty = true; save();
    }
    private void clearAll() {
        pushUndo();
        dFull = true; texDirty = true;
        for (int i = 0; i < data.area(); i++) setPixelIfChanged(i, Palette.BACKING);
        save();
    }

    private void flushMini() {
        if (pendIdx.isEmpty() || pos == null) return;
        long now = System.currentTimeMillis();
        if (now - lastFlush < 50 && painting) return;
        int[] idx = new int[pendIdx.size()];
        int[] val = new int[pendVal.size()];
        for (int i = 0; i < idx.length; i++) { idx[i] = pendIdx.get(i); val[i] = pendVal.get(i); }
        ClientHandlers.sendToServer(new CanvasMiniUpdatePacket(idx, val, pos));
        pendIdx.clear(); pendVal.clear();
        lastFlush = now;
    }

    private void save() {
        if (!dirty) return;
        if (pos != null) {
            ClientHandlers.sendToServer(new CanvasUpdatePacket(data.pixels.clone(), pos));
            flushMini();
        } else {
            onSave.save(data.width, data.height, data.pixels.clone());
        }
        dirty = false;
    }

    // ---- stroke ----

    private boolean beginStroke() {
        // Painting is free — no dye is consumed per stroke. The only thing that costs
        // pigment is the magic appraisal (which consumes one magic dye on the server).
        if (pos != null) {
            ClientHandlers.sendToServer(new CanvasBeginStrokePacket(pos));
        }
        return true;
    }
    private void endStroke() {
        // record the dye colour just used so we can charge one of it on close
        if (tool != Tool.ERASER && currentSource.type == PaintSource.DYE && currentSource.indices.length > 0)
            usedDyes.add(currentSource.indices[0]);
        if (pos == null) {
            save();
            return;
        }
        ClientHandlers.sendToServer(new CanvasEndStrokePacket(pos));
        flushMini();
        save();
    }

    // ---- mixer / universal ----

    private void selectDye(int i) {
        currentColor = Palette.dyeRgb(i);
        currentSource = PaintSource.dye(i);
    }
    private void useUniversal() {
        currentColor = (ur << 16) | (ug << 8) | ub;
        currentSource = PaintSource.universal();
    }
    private void setSlider(int i, int mx) {
        int v = (int) Math.round((mx - sliderX[i]) * 255.0 / sliderW);
        v = clamp(v);
        if (i == 0) ur = v; else if (i == 1) ug = v; else ub = v;
        currentColor = (ur << 16) | (ug << 8) | ub;
        currentSource = PaintSource.universal();
    }
    private void setBrushSize(int mx) {
        int v = (int) Math.round((mx - brushSliderX) * (BRUSH_MAX - BRUSH_MIN) / (double) brushSliderW) + BRUSH_MIN;
        brushSize = Math.max(BRUSH_MIN, Math.min(BRUSH_MAX, v));
    }
    private static int[] toArr(List<Integer> l) { int[] a = new int[l.size()]; for (int i = 0; i < a.length; i++) a[i] = l.get(i); return a; }

    private void openSize() {
        Minecraft.getInstance().setScreen(new CanvasSizeScreen(data, (w, h) -> {
            data.resampleTo(w, h);
            texDirty = true; dFull = true; dirty = true;
            save();
            Minecraft.getInstance().setScreen(this);
        }));
    }

    private void openAiSettings() {
        Minecraft.getInstance().setScreen(new AiSettingsScreen(this));
    }

    /** Structured effect parsed out of the AI reply. */
    private record Effect(byte type, String id, byte amp, String desc) {}

    private void doAiRecognize() {
        if (aiBusy) return;
        if (!allowAppraisal) return;
        // a blank canvas gets nothing; skip so players can't farm free loot
        int painted = 0;
        for (int v : data.pixels) if (v >= 0) painted++;
        if (painted == 0) {
            aiResult = "画布还是空白的，先画点什么再鉴定吧。";
            return;
        }
        aiBusy = true;
        aiResult = "魔法解析中…";
        com.example.paintingmod.ai.AiConfig cfg = com.example.paintingmod.ai.AiConfig.get();
        if (!cfg.hasToken()) {
            aiBusy = false;
            aiResult = "尚未注入魔力（Token），请先翻开右侧「魔法书」。";
            openAiSettings();
            return;
        }
        // snapshot the artwork BEFORE we wipe the canvas
        final int w = data.width, h = data.height;
        final int[] px = data.pixels.clone();
        aiThinking = "";
        com.example.paintingmod.ai.AiRecognition.recognize(data.copy(),
                res -> {
                    aiBusy = false;
                    if (res.reasoning != null && !res.reasoning.isBlank()) aiThinking = res.reasoning;
                    Effect e = resolveEffect(res.content);
                    // auto-grant + auto-clear: the server is authoritative for the grant
                    for (int i = 0; i < data.area(); i++) data.pixels[i] = Palette.BACKING;
                    texDirty = true; dFull = true; dirty = true; save();
                    ClientHandlers.sendToServer(new CanvasAppraisalPacket(
                            Optional.ofNullable(pos), e.type, e.id, e.amp, e.desc, w, h, px));
                    String shown = (e.desc == null ? "" : e.desc).replace("\n", " ").trim();
                    aiResult = "§d魔法鉴定完成：识别为 §e" + effectLabel(e.type) + "§r。\n"
                            + (shown.isEmpty() ? "（无法识别具体内容）" : shown)
                            + "\n§7画作已封存于「魔法画稿」，奖励已发放。";
                    // 把 AI 的推理过程发给玩家（方便复盘、下次照着画）；受「显示思考过程」开关控制
                    if (AiConfig.get().showThinking && aiThinking != null && !aiThinking.isBlank()
                            && Minecraft.getInstance().player != null)
                        Minecraft.getInstance().player.sendSystemMessage(
                                Component.literal("§d[AI 思考过程] §f" + truncateThinking(aiThinking)));
                },
                err -> { aiBusy = false; aiThinking = ""; aiResult = err; });
    }

    /** Chinese (and a few aliases) -> vanilla mob-effect path. Lets the AI answer with either
     *  an English id or a plain Chinese name and still resolve to a real status effect. */
    private static final Map<String, String> CHINESE_EFFECT = new HashMap<>();
    static {
        String[][] m = {
            {"夜视", "night_vision"}, {"夜视效果", "night_vision"},
            {"抗性", "resistance"}, {"抗性提升", "resistance"},
            {"再生", "regeneration"}, {"生命恢复", "regeneration"},
            {"防火", "fire_resistance"}, {"抗火", "fire_resistance"},
            {"缓降", "slow_falling"},
            {"速度", "speed"}, {"迅捷", "speed"},
            {"力量", "strength"},
            {"跳跃", "jump_boost"}, {"跳跃提升", "jump_boost"},
            {"隐身", "invisibility"},
            {"水下呼吸", "water_breathing"}, {"水肺", "water_breathing"},
            {"伤害吸收", "absorption"},
            {"生命提升", "health_boost"},
            {"发光", "glowing"},
            {"幸运", "luck"},
            {"中毒", "poison"},
            {"虚弱", "weakness"},
            {"缓慢", "slowness"},
            {"饥饿", "hunger"},
            {"失明", "blindness"},
            {"飘浮", "levitation"},
            {"治疗", "instant_health"}, {"瞬间治疗", "instant_health"},
            {"伤害", "instant_damage"}, {"瞬间伤害", "instant_damage"},
            {"饱和", "saturation"},
            {"凋零", "wither"},
            {"潮涌", "conduit_power"},
            {"海豚", "dolphins_grace"},
            {"挖掘疲劳", "mining_fatigue"},
            {"反胃", "nausea"},
            {"不祥之兆", "bad_omen"},
            {"村庄英雄", "hero_of_the_village"},
        };
        for (String[] p : m) CHINESE_EFFECT.put(p[0], p[1]);
    }

    /**
     * Map an AI reply to a structured effect. The AI returns a rich description plus a
     * "方向" weight line (物品 / 生物 / 状态) and a candidate id for each direction. We pick
     * the highest-weight direction that actually has a valid candidate; weather/lightning
     * override the weights entirely. A spent magic dye is always refunded with something
     * grantable, so we fall back to a random real item if nothing parses.
     */
    private Effect resolveEffect(String text) {
        String lower = (text == null ? "" : text).toLowerCase(Locale.ROOT);
        String desc = (text == null ? "" : text.trim());
        if (text != null) {
            for (String ln : text.split("\n")) {
                String t = ln.trim();
                if (t.startsWith("描述")) { desc = t.replaceFirst("(?i)^描述\\s*[:：]", "").trim(); }
                else if (t.startsWith("思考")) {
                    String th = t.replaceFirst("(?i)^思考(过程)?\\s*[:：]", "").trim();
                    if (!th.isEmpty() && (aiThinking == null || aiThinking.isBlank())) aiThinking = th;
                }
            }
        }

        // 兼容「JSON 格式」提示词（如用户自写的 action/item/id 结构）：先尝试解析
        Effect je = tryParseJson(text);
        if (je != null) return je;

        // ---- 解析结构化候选行 ----
        String dirLine  = field(lower, "方向");
        String itemLine = field(lower, "物品");
        String entLine  = field(lower, "生物");
        String effLine  = field(lower, "状态");
        String wLine    = field(lower, "天气");
        String cmdLine  = field(lower, "指令");

        ResourceLocation itemRl = parseMcId(itemLine);
        ResourceLocation entRl  = parseMcId(entLine);
        ResourceLocation effRl  = resolveEffectId(effLine);
        byte effAmp = (effLine != null && effLine.contains("强")) ? (byte) 1 : (byte) 0;

        // ---- 物品优先级大于一切：物品行给出合法且真实存在的 id，就发物品 ----
        if (itemRl != null && RewardTable.isItemAllowed(itemRl))
            return new Effect(CanvasAppraisalPacket.EFFECT_ITEM, itemRl.toString(), (byte) 0, desc);
        // 其次生物
        if (entRl != null && RewardTable.isEntityAllowed(entRl))
            return new Effect(CanvasAppraisalPacket.EFFECT_ENTITY, entRl.toString(), (byte) 0, desc);
        // 再次状态效果
        if (effRl != null)
            return new Effect(CanvasAppraisalPacket.EFFECT_POTION, effRl.toString(), effAmp, desc);
        // 然后天气
        if (wLine != null) {
            String w = wLine.trim();
            if (w.contains("闪电") || w.contains("lightning"))
                return new Effect(CanvasAppraisalPacket.EFFECT_LIGHTNING, null, (byte) 0, desc);
            if (w.contains("晴") || w.contains("clear") || w.contains("sun"))
                return new Effect(CanvasAppraisalPacket.EFFECT_WEATHER_CLEAR, null, (byte) 0, desc);
            if (w.contains("雷") || w.contains("thunder") || w.contains("暴"))
                return new Effect(CanvasAppraisalPacket.EFFECT_WEATHER_THUNDER, null, (byte) 0, desc);
            if (w.contains("雨") || w.contains("rain"))
                return new Effect(CanvasAppraisalPacket.EFFECT_WEATHER_RAIN, null, (byte) 0, desc);
        }
        // 最后指令（服务端仍按白名单校验）
        if (cmdLine != null) {
            String cmd = RewardTable.findCommand(cmdLine.trim());
            if (cmd != null)
                return new Effect(CanvasAppraisalPacket.EFFECT_COMMAND, cmd, (byte) 0, desc);
        }

        // 没有任何可识别结果 -> 兜底发一个清单内真实物品，保证鉴定总有产出
        RewardTable.Entry e = RewardTable.random();
        return new Effect(CanvasAppraisalPacket.EFFECT_ITEM, e.id.toString(), (byte) 0, desc);
    }

    /** Try to parse a JSON-style AI reply ({action, id, ...}). Returns null if it isn't
     *  JSON or the structure is unrecognised, so the caller falls back to the 6-line parser. */
    private Effect tryParseJson(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("```")) {                       // strip ```json ... ``` fences
            int nl = t.indexOf('\n');
            if (nl >= 0) t = t.substring(nl + 1);
            int end = t.lastIndexOf("```");
            if (end >= 0) t = t.substring(0, end);
            t = t.trim();
        }
        com.google.gson.JsonObject o;
        try {
            o = com.google.gson.JsonParser.parseString(t).getAsJsonObject();
        } catch (Exception e) { return null; }
        if (!o.has("action")) return null;

        String action = o.get("action").getAsString().toLowerCase(java.util.Locale.ROOT);
        String id = o.has("id") ? o.get("id").getAsString() : null;
        String rk = o.has("thinking") ? o.get("thinking").getAsString()
                  : (o.has("reasoning") ? o.get("reasoning").getAsString() : null);
        if (rk != null && !rk.isBlank() && (aiThinking == null || aiThinking.isBlank())) aiThinking = rk;

        String desc = (id != null ? id : action);
        return switch (action) {
            case "item", "block" -> {
                ResourceLocation rl = parseMcId(id);
                yield (rl != null && RewardTable.isItemAllowed(rl))
                        ? new Effect(CanvasAppraisalPacket.EFFECT_ITEM, rl.toString(), (byte) 0, desc) : null;
            }
            case "entity", "summon" -> {
                ResourceLocation rl = parseMcId(id);
                yield (rl != null && RewardTable.isEntityAllowed(rl))
                        ? new Effect(CanvasAppraisalPacket.EFFECT_ENTITY, rl.toString(), (byte) 0, desc) : null;
            }
            case "effect", "potion" -> {
                ResourceLocation rl = resolveEffectId(id);
                yield rl != null ? new Effect(CanvasAppraisalPacket.EFFECT_POTION, rl.toString(), (byte) 0, desc) : null;
            }
            case "command" -> {
                // The AI may put the command in either `cmd` or `id` (our JSON prompt uses
                // both keys; some models answer with id only, e.g. {"action":"command","id":"weather rain"}).
                // Fall back to id so those replies aren't mis-read as a plain text and dropped to the
                // random-reward fallback (which used to hand out a random item like 活塞).
                String cmd = o.has("cmd") ? o.get("cmd").getAsString()
                          : (o.has("id") ? o.get("id").getAsString() : null);
                if (cmd == null) yield null;
                String lc = cmd.toLowerCase(java.util.Locale.ROOT);
                // Weather-style commands are routed to the dedicated weather effect so they work
                // out of the box (no rewards.json needed) and read nicely as 下雨 / 晴天 / 雷暴 / 闪电.
                if (lc.contains("lightning") || lc.contains("闪电"))
                    yield new Effect(CanvasAppraisalPacket.EFFECT_LIGHTNING, null, (byte) 0, desc);
                if (lc.contains("thunder") || lc.contains("雷暴") || lc.contains("雷"))
                    yield new Effect(CanvasAppraisalPacket.EFFECT_WEATHER_THUNDER, null, (byte) 0, desc);
                if (lc.contains("clear") || lc.contains("晴"))
                    yield new Effect(CanvasAppraisalPacket.EFFECT_WEATHER_CLEAR, null, (byte) 0, desc);
                if (lc.contains("rain") || lc.contains("雨") || lc.contains("weather"))
                    yield new Effect(CanvasAppraisalPacket.EFFECT_WEATHER_RAIN, null, (byte) 0, desc);
                // Any other command only runs if it is allow-listed in rewards.json.
                yield RewardTable.isCommandAllowed(cmd)
                        ? new Effect(CanvasAppraisalPacket.EFFECT_COMMAND, cmd, (byte) 0, desc) : null;
            }
            case "weather" -> {
                String w = (id == null ? "" : id).toLowerCase(java.util.Locale.ROOT);
                if (w.contains("闪电") || w.contains("lightning"))
                    yield new Effect(CanvasAppraisalPacket.EFFECT_LIGHTNING, null, (byte) 0, desc);
                if (w.contains("雷") || w.contains("thunder") || w.contains("暴"))
                    yield new Effect(CanvasAppraisalPacket.EFFECT_WEATHER_THUNDER, null, (byte) 0, desc);
                if (w.contains("雨") || w.contains("rain"))
                    yield new Effect(CanvasAppraisalPacket.EFFECT_WEATHER_RAIN, null, (byte) 0, desc);
                yield new Effect(CanvasAppraisalPacket.EFFECT_WEATHER_CLEAR, null, (byte) 0, desc);
            }
            default -> null;
        };
    }

    /** Extract the value of a "keyword：value" line (colon OR space separated). */
    private static String field(String lower, String kw) {
        if (lower == null) return null;
        for (String ln : lower.split("\n")) {
            String t = ln.trim();
            if (!t.startsWith(kw)) continue;
            int i = kw.length();
            if (i < t.length()) {
                char c = t.charAt(i);
                if (c != '：' && c != ':' && c != ' ') continue;
            }
            String rest = t.substring(i).replaceFirst("^[:：\\s]+", "").trim();
            return rest.isEmpty() ? null : rest;
        }
        return null;
    }

    /** Grab the first decimal number that follows a keyword (used for the 方向 weights). */
    private static Double grabNum(String s, String kwPattern) {
        if (s == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(?:" + kwPattern + ")\\D*?([0-9]+(?:\\.[0-9]+)?)").matcher(s);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /** Resolve a status-effect name (English id or Chinese) to a real mob-effect id. */
    private static ResourceLocation resolveEffectId(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("无") || t.equals("none") || t.equals("null")) return null;
        ResourceLocation rl = ResourceLocation.tryParse(t.contains(":") ? t : "minecraft:" + t);
        if (rl != null && BuiltInRegistries.MOB_EFFECT.containsKey(rl)) return rl;
        String en = CHINESE_EFFECT.get(t);
        if (en != null) {
            ResourceLocation r2 = ResourceLocation.tryParse("minecraft:" + en);
            if (r2 != null && BuiltInRegistries.MOB_EFFECT.containsKey(r2)) return r2;
        }
        for (var e : CHINESE_EFFECT.entrySet()) {
            if (t.contains(e.getKey())) {
                ResourceLocation r3 = ResourceLocation.tryParse("minecraft:" + e.getValue());
                if (r3 != null && BuiltInRegistries.MOB_EFFECT.containsKey(r3)) return r3;
            }
        }
        return null;
    }

    private static String effectLabel(byte type) {
        return switch (type) {
            case CanvasAppraisalPacket.EFFECT_ITEM -> "物品";
            case CanvasAppraisalPacket.EFFECT_ENTITY -> "生物";
            case CanvasAppraisalPacket.EFFECT_POTION -> "状态效果";
            case CanvasAppraisalPacket.EFFECT_WEATHER_CLEAR -> "晴天";
            case CanvasAppraisalPacket.EFFECT_WEATHER_RAIN -> "下雨";
            case CanvasAppraisalPacket.EFFECT_WEATHER_THUNDER -> "雷暴";
            case CanvasAppraisalPacket.EFFECT_LIGHTNING -> "闪电";
            default -> "未知";
        };
    }

    /** Pull a minecraft:<id> out of a directive string, accepting a few phrasings. */
    private static ResourceLocation parseMcId(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        // accept "minecraft:<id>" or bare "<id>" (-> minecraft:<id>) or "<namespace>:<id>" (mod items)
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:ID|物品|生物|MINECRAFT)\\s*[:：]\\s*([a-z0-9_.-]+:[a-z0-9_./-]+|[a-z0-9_./-]+)")
                .matcher(lower);
        if (m.find()) {
            String id = m.group(1);
            if (!id.contains(":")) id = "minecraft:" + id;
            ResourceLocation r = ResourceLocation.tryParse(id);
            if (r != null) return r;
        }
        m = java.util.regex.Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)").matcher(lower);
        if (m.find()) return ResourceLocation.tryParse(m.group(1));
        return null;
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        for (Rect b : buttons) if (b.hit(mx, my)) { b.action().run(); return true; }
        for (DyeChip c : dyeChips) if (c.hit(mx, my)) {
            if (!chipUnlocked(c.index())) {
                var pp = Minecraft.getInstance().player;
                if (pp != null) pp.sendSystemMessage(Component.literal("§7需要先拥有 " + dyeName(c.index()) + " 染料，才能选择该颜色"));
                return true;
            }
            selectDye(c.index());
            return true;
        }
        // brush size slider region (takes priority in its own band)
        if (draggingSlider < 0 && !draggingBrush && mx >= brushSliderX - 2 && mx <= brushSliderX + brushSliderW + 2
                && my >= brushSliderY - 3 && my <= brushSliderY + SLIDER_H + 3) {
            draggingBrush = true;
            setBrushSize(mx);
            return true;
        }
        if (universalAvailable && draggingSlider < 0) {
            for (int i = 0; i < 3; i++) {
                if (mx >= sliderX[i] - 2 && mx <= sliderX[i] + sliderW + 2
                        && my >= sliderY[i] - 3 && my <= sliderY[i] + SLIDER_H + 3) {
                    draggingSlider = i;
                    setSlider(i, mx);
                    return true;
                }
            }
        }
        if (mx >= canvasX && my >= canvasY && mx < canvasX + canvasDispW && my < canvasY + canvasDispH) {
            boolean isPaint = (tool == Tool.BRUSH || tool == Tool.ERASER || tool == Tool.FILL);
            if (tool == Tool.EYEDROPPER) { tryPaint(mx, my); return true; }
            if (isPaint) {
                if (!beginStroke()) return true;
                pushUndo();
                if (tryPaint(mx, my)) { painting = true; flushMini(); }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider >= 0) { setSlider(draggingSlider, (int) mouseX); return true; }
        if (draggingBrush) { setBrushSize((int) mouseX); return true; }
        if (painting) { tryPaint((int) mouseX, (int) mouseY); flushMini(); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider >= 0) { draggingSlider = -1; return true; }
        if (draggingBrush) { draggingBrush = false; return true; }
        if (painting) { painting = false; endStroke(); return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ---- render ----

    private void updateTexture() {
        if (!texDirty) return;
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        NativeImage existing = (dynTex != null) ? dynTex.getPixels() : null;
        // self-heal: if the canvas was resized (调整尺寸 / resampleTo) the old NativeImage
        // would be the wrong size and encodePixel would write out of bounds -> rebuild it.
        if (dynRl == null || existing == null
                || existing.getWidth() != data.width || existing.getHeight() != data.height) {
            releaseTexture();
            NativeImage img = new NativeImage(data.width, data.height, true);
            dynTex = new DynamicTexture(img);
            dynRl = ResourceLocation.fromNamespaceAndPath("paintingmod", "dynamic/canvas_" + System.nanoTime());
            tm.register(dynRl, dynTex);
            dFull = true;
        }
        NativeImage img = dynTex.getPixels();
        if (dFull || dMaxX < 0) {
            encodeAll(img);
            dFull = false;
            dMaxX = -1;
        } else {
            for (int y = dMinY; y <= dMaxY; y++)
                for (int x = dMinX; x <= dMaxX; x++)
                    encodePixel(img, x, y, data.pixels[y * data.width + x]);
            dMaxX = -1;
        }
        dynTex.upload();
        texDirty = false;
    }

    private void encodePixel(NativeImage img, int x, int y, int v) {
        int r, g, b, a;
        if (v < 0) { r = g = b = 0; a = 0; }
        else { r = (v >> 16) & 0xFF; g = (v >> 8) & 0xFF; b = v & 0xFF; a = 0xFF; }
        // data row 0 = top; blit maps texel row 0 to the top of the display rect (no flip)
        img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
    }

    private void encodeAll(NativeImage img) {
        for (int y = 0; y < data.height; y++)
            for (int x = 0; x < data.width; x++)
                encodePixel(img, x, y, data.pixels[y * data.width + x]);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        updateTexture();

        Minecraft mc = Minecraft.getInstance();
        g.fill(0, 0, this.width, this.height, 0xEE15151A);

        // ---- top title bar ----
        g.fill(0, 0, this.width, 40, 0xFF1E1E26);
        g.fill(0, 40, this.width, 42, 0xFF3A6EA5);
        // pseudo-bold title (double draw)
        g.drawCenteredString(font, title, this.width / 2 + 1, 10, 0xFF000000);
        g.drawCenteredString(font, title, this.width / 2, 10, 0xFFFFFFFF);
        Component status = Component.literal("画布 " + data.width + "×" + data.height
                + "    拥有染料 " + ownedDyes.size() + "/16"
                + "    画笔 " + brushSize + "px");
        g.drawCenteredString(font, status, this.width / 2, 26, 0xFF9AA0AA);

        // ---- side panels ----
        drawPanel(g, leftX, 46, leftW, this.height - 52);
        drawPanel(g, rightX, 46, rightW, this.height - 52);
        g.drawString(font, "§b颜料", leftX + 8, 50, 0xFFFFFFFF);
        g.drawString(font, "§b工具", rightX + 6, 50, 0xFFFFFFFF);

        // ---- canvas ----
        g.fill(canvasX - 4, canvasY - 4, canvasX + canvasDispW + 4, canvasY + canvasDispH + 4, 0xFF0A0A0C);
        g.fill(canvasX - 2, canvasY - 2, canvasX + canvasDispW + 2, canvasY + canvasDispH + 2, 0xFF3A3A44);
        g.fill(canvasX, canvasY, canvasX + canvasDispW, canvasY + canvasDispH, 0xFF000000 | Palette.PAPER);
        if (dynRl != null) {
            // scale the FULL data.width x data.height texture into the display rect
            g.blit(dynRl, canvasX, canvasY, canvasDispW, canvasDispH,
                    0f, 0f, data.width, data.height, data.width, data.height);
        }

        // ---- dye chips (4x4) ----
        for (DyeChip c : dyeChips) {
            g.fill(c.x(), c.y(), c.x() + c.w(), c.y() + c.h(), 0xFF23232B);
            var dyeItem = Palette.dyeItem(c.index());
            ItemStack stack = dyeItem != null ? new ItemStack(dyeItem) : new ItemStack(Items.BARRIER);
            g.renderItem(stack, c.x() + c.w() / 2 - 8, c.y() + c.h() / 2 - 8);
            int rgb = Palette.dyeRgb(c.index());
            g.fill(c.x() + c.w() - 6, c.y() + c.h() - 6, c.x() + c.w() - 1, c.y() + c.h() - 1, 0xFF000000 | rgb);
            boolean owned = ownedDyes.contains(c.index());
            boolean selected = currentColor == rgb && currentSource.type == PaintSource.DYE
                    && currentSource.indices.length == 1 && currentSource.indices[0] == c.index();
            if (!chipUnlocked(c.index())) {
                // lock overlay: dim the chip so un-owned / un-unlocked colours read as unusable
                g.fill(c.x(), c.y(), c.x() + c.w(), c.y() + c.h(), 0xAA0A0A0C);
            }
            if (selected)
                g.renderOutline(c.x() - 1, c.y() - 1, c.w() + 2, c.h() + 2, 0xFFFFFFFF);
        }

        // ---- dividers ----
        for (int[] d : dividers) g.fill(d[0], d[1], d[2], d[1] + 1, 0xFF34343E);

        // ---- current color preview + source ----
        int lx = leftX + 8;
        g.fill(lx, infoY, lx + 30, infoY + 24, 0xFF000000 | (currentColor < 0 ? Palette.PAPER : currentColor));
        g.renderOutline(lx - 1, infoY - 1, 32, 26, 0xFF666677);
        String src;
        if (currentSource.type == PaintSource.NONE) src = "自由取色";
        else if (currentSource.type == PaintSource.DYE) src = dyeName(currentSource.indices[0]);
        else if (currentSource.type == PaintSource.MIX) src = "混合(" + currentSource.indices.length + ")";
        else src = "魔法色";
        g.drawString(font, "§7当前画笔", lx + 38, infoY + 3, 0xFF9AA0AA);
        g.drawString(font, src, lx + 38, infoY + 14, 0xFFEEEEEE);

        // ---- RGB sliders (horizontal) ----
        if (universalAvailable) {
            g.drawString(font, "§d魔法调色 RGB", lx, sliderHeaderY, 0xFFD9A8FF);
            drawSlider(g, 0, "R", ur, 0xFFE05555);
            drawSlider(g, 1, "G", ug, 0xFF55C46A);
            drawSlider(g, 2, "B", ub, 0xFF5599E0);
            int prev = (ur << 16) | (ug << 8) | ub;
            g.fill(lx, previewY, lx + 30, previewY + 24, 0xFF000000 | prev);
            g.renderOutline(lx - 1, previewY - 1, 32, 26, 0xFF666677);
            g.drawString(font, "§7预览", lx + 38, previewY + 3, 0xFF9AA0AA);
            g.drawString(font, "#" + String.format("%02X%02X%02X", ur, ug, ub), lx + 38, previewY + 14, 0xFFFFFFFF);
        }

        // ---- brush size slider ----
        g.drawString(font, "§b画笔粗细", lx, brushSliderY - 12, 0xFFD9A8FF);
        drawHSlider(g, brushSliderX, brushSliderY, brushSliderW, brushSize, BRUSH_MIN, BRUSH_MAX, 0xFF9C6BDF);
        String bv = brushSize + "px";
        g.drawString(font, bv, brushSliderX + brushSliderW - font.width(bv) - 3, brushSliderY + 2, 0xFFFFFFFF);

        // ---- right toolbar buttons ----
        for (Rect b : buttons) drawButton(g, b, mouseX, mouseY);

        // ---- bottom info strip (AI result / reward) ----
        int stripY = this.height - 56;
        int stripX = canvasX - 4;
        int stripW = canvasDispW + 8;
        if (stripW < 240) { stripX = leftX + leftW + 10; stripW = rightX - 10 - stripX; }
        if (!aiResult.isEmpty()) {
            g.fill(stripX, stripY, stripX + stripW, this.height - 6, 0xEE0E0E14);
            g.renderOutline(stripX, stripY, stripW, this.height - 6 - stripY, 0xFF9C6BDF);
            g.drawString(font, aiBusy ? "§d魔法解析中…" : "§d魔法鉴定结果", stripX + 6, stripY + 5, 0xFFD9A8FF);
            var lines = font.split(Component.literal(aiResult), stripW - 12);
            int n = Math.min(lines.size(), 2);
            for (int i = 0; i < n; i++)
                g.drawString(font, lines.get(i), stripX + 6, stripY + 18 + i * 11, 0xFFE8E8E8);
            if (AiConfig.get().showThinking && aiThinking != null && !aiThinking.isBlank()) {
                String head = "§dAI 思考：" + truncateThinking(aiThinking);
                var tl = font.split(Component.literal(head), stripW - 12);
                int ty = stripY + 18 + n * 11 + 1;
                if (!tl.isEmpty())
                    g.drawString(font, tl.get(0), stripX + 6, ty, 0xFFC9B6E6);
            }
        }
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xCC101018);
        g.renderOutline(x, y, w, h, 0xFF2E2E38);
    }

    private void drawButton(GuiGraphics g, Rect b, int mouseX, int mouseY) {
        boolean on = b.active().getAsBoolean();
        boolean hover = b.hit(mouseX, mouseY);
        int base = on ? 0xFF3A6EA5 : (hover ? 0xFF3C3C48 : 0xFF2A2A34);
        int x1 = b.x(), y1 = b.y(), x2 = b.x() + b.w(), y2 = b.y() + b.h();
        g.fill(x1, y1, x2, y2, base);
        // MC-style bevel: light top+left, dark bottom+right
        g.fill(x1, y1, x2, y1 + 1, 0x50FFFFFF);
        g.fill(x1, y1, x1 + 1, y2, 0x50FFFFFF);
        g.fill(x1, y2 - 1, x2, y2, 0x70000000);
        g.fill(x2 - 1, y1, x2, y2, 0x70000000);
        g.drawCenteredString(font, b.label(), x1 + b.w() / 2, y1 + (b.h() - 8) / 2, on ? 0xFFFFFFFF : 0xFFDDDDDD);
    }

    private void drawSlider(GuiGraphics g, int i, String name, int value, int color) {
        int x = sliderX[i], y = sliderY[i];
        // track
        g.fill(x, y, x + sliderW, y + SLIDER_H, 0xFF23232B);
        // filled portion
        int fw = (int) (sliderW * value / 255.0);
        g.fill(x, y, x + fw, y + SLIDER_H, color);
        // handle
        int hx = x + fw;
        g.fill(hx - 1, y - 2, hx + 2, y + SLIDER_H + 2, 0xFFFFFFFF);
        // label + numeric value
        g.drawString(font, name, x + 3, y + 2, 0xFFFFFFFF);
        String val = String.valueOf(value);
        g.drawString(font, val, x + sliderW - font.width(val) - 3, y + 2, 0xFFFFFFFF);
    }

    private void drawHSlider(GuiGraphics g, int x, int y, int w, int value, int min, int max, int color) {
        g.fill(x, y, x + w, y + SLIDER_H, 0xFF23232B);
        int fw = (int) (w * (value - min) / (double) (max - min));
        g.fill(x, y, x + fw, y + SLIDER_H, color);
        int hx = x + fw;
        g.fill(hx - 1, y - 2, hx + 2, y + SLIDER_H + 2, 0xFFFFFFFF);
        g.drawString(font, "−", x + 3, y + 2, 0xFFFFFFFF);
        g.drawString(font, "+", x + w - 9, y + 2, 0xFFFFFFFF);
    }

    private static String dyeName(int idx) {
        return switch (idx) {
            case 0 -> "白色"; case 1 -> "橙色"; case 2 -> "品红色"; case 3 -> "淡蓝色";
            case 4 -> "黄色"; case 5 -> "黄绿色"; case 6 -> "粉红色"; case 7 -> "灰色";
            case 8 -> "淡灰色"; case 9 -> "青色"; case 10 -> "紫色"; case 11 -> "蓝色";
            case 12 -> "棕色"; case 13 -> "绿色"; case 14 -> "红色"; case 15 -> "黑色";
            default -> "染料";
        };
    }
}
