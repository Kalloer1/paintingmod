package com.example.paintingmod.client;

import com.example.paintingmod.ai.AiConfig;
import com.example.paintingmod.ai.RewardTable;
import com.example.paintingmod.canvas.PaintingData;
import com.example.paintingmod.canvas.Palette;
import com.example.paintingmod.config.ModConfigFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Sends the player's painting to a vision-capable chat model (SiliconFlow by default,
 * but the base URL is configurable) and returns the textual description. Runs off the
 * render thread; results are handed back on the main thread.
 */
public final class AiRecognition {
    private static final Logger LOGGER = LogManager.getLogger("PaintingModAI");
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    /** 非纸像素数低于此阈值时，跳过 API、改本地按主色发基础奖励（省 token，毫秒级）。 */
    private static final int SPARSE_THRESHOLD = 20;

    /** What the vision model gave us back. {@code reasoning} is the model's thinking
     *  trace (GLM "深度思考" -> reasoning_content). May be blank for models without it. */
    public static final class Result {
        public final String content;
        public final String reasoning;
        public Result(String content, String reasoning) {
            this.content = content == null ? "" : content;
            this.reasoning = reasoning == null ? "" : reasoning;
        }
    }

    public static void recognize(PaintingData data, Consumer<Result> onResult, Consumer<String> onError) {
        AiConfig cfg = AiConfig.get();
        if (!cfg.hasToken()) {
            onError.accept("尚未配置 API Token，请先在「AI 设置」中填入你的硅基流动 Token。");
            return;
        }
        // snapshot pixels onto a fresh array (avoid mutation races)
        final int w = data.width, h = data.height;
        final int[] px = data.pixels.clone();

        // ---- local sparse fallback: skip the API entirely for tiny sketches ----
        int nonPaper = 0, rSum = 0, gSum = 0, bSum = 0, n = 0;
        for (int v : px) {
            if (v < 0) continue;               // paper / blank pixel
            nonPaper++;
            rSum += (v >> 16) & 0xFF;
            gSum += (v >> 8) & 0xFF;
            bSum += v & 0xFF;
            n++;
        }
        if (nonPaper > 0 && nonPaper < SPARSE_THRESHOLD) {
            int dr = rSum / n, dg = gSum / n, db = bSum / n;
            String grant = ModConfigFiles.localColorReward(dr, dg, db);
            if (grant != null) {
                final String content = "Sparse sketch — local appraisal by dominant colour.\nGRANT|" + grant;
                Minecraft.getInstance().execute(() -> onResult.accept(new Result(content, "")));
                return;
            }
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                String b64 = encodePng(w, h, px, cfg.upscale);
                RewardTable.reload(); // pick up any edits to rewards.json
                String body = buildBody(cfg, b64);
                String url = cfg.apiBase;
                if (!url.endsWith("/")) url += "/";
                url += "chat/completions";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + cfg.apiToken)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .timeout(java.time.Duration.ofSeconds(60))
                        .build();
                HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    return new Result("API 错误 (" + resp.statusCode() + "): " + resp.body(), "");
                }
                return parseContent(resp.body());
            } catch (Exception e) {
                LOGGER.error("AI recognition failed", e);
                return new Result("识别失败: " + e.getMessage(), "");
            }
        }).whenComplete((result, err) -> {
            Minecraft.getInstance().execute(() -> {
                if (err != null) onError.accept("识别异常: " + err.getMessage());
                else onResult.accept(result);
            });
        });
    }

    private static String encodePng(int w, int h, int[] px, boolean upscale) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] rgb = new int[w * h];
        for (int i = 0; i < rgb.length; i++) {
            int v = px[i];
            rgb[i] = (v < 0) ? Palette.PAPER : v;
        }
        img.setRGB(0, 0, w, h, rgb, 0, w);
        if (upscale) {
            int s = 4;
            BufferedImage big = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = big.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(img, 0, 0, w * s, h * s, null);
            g.dispose();
            img = big;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * Build the request body.
     *
     * Caching: the large STATIC prefix (instructions + reward allowlist) is placed in the
     * {@code system} role and stays byte-identical across calls, so SiliconFlow reuses the
     * cached prefix and only bills the image + tiny trigger text each round. The only
     * per-request variable is the image in the {@code user} message.
     */
    private static String buildBody(AiConfig cfg, String b64) {
        // 1) stable system prefix — hot-editable instructions + authoritative allowlist
        String systemText = ModConfigFiles.readAppraisalPrompt() + "\n\n" + allowlist();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemText);

        // 2) user message — minimal trigger text + the picture (the only changing part)
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", "Appraise this pixel painting and follow the instructions exactly.");

        JsonObject imgPart = new JsonObject();
        imgPart.addProperty("type", "image_url");
        JsonObject imgUrl = new JsonObject();
        imgUrl.addProperty("url", "data:image/png;base64," + b64);
        imgPart.add("image_url", imgUrl);

        JsonArray content = new JsonArray();
        content.add(textPart);
        content.add(imgPart);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.add("content", content);

        JsonArray messages = new JsonArray();
        messages.add(sysMsg);
        messages.add(userMsg);

        JsonObject root = new JsonObject();
        root.addProperty("model", cfg.model);
        root.add("messages", messages);
        root.addProperty("max_tokens", cfg.maxTokens);
        root.addProperty("temperature", cfg.temperature);
        // 深度思考：仅 GLM 系模型支持，且按设置开启；其它模型(硅基流动 Qwen 等)忽略该字段
        if (cfg.think && cfg.model.toLowerCase(java.util.Locale.ROOT).contains("glm")) {
            JsonObject th = new JsonObject();
            th.addProperty("type", "enabled");
            root.add("thinking", th);
        }
        return root.toString();
    }

    /** Authoritative, stable reward vocabulary injected into the system prompt. */
    private static String allowlist() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALLOWED REWARD VOCABULARY (choose ONLY from these, never invent):\n");
        sb.append("· items: ").append(String.join(" ", RewardTable.itemIdsVanilla())).append("\n");
        sb.append("· entities: ").append(String.join(" ", RewardTable.entityIdsVanilla())).append("\n");
        sb.append("· effects: ").append(String.join(" ", RewardTable.effectIds())).append("\n");
        sb.append("· commands (keyword->command): ").append(String.join("  ", RewardTable.commandKeys())).append("\n");
        return sb.toString();
    }

    private static Result parseContent(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("error")) {
                JsonObject e = o.getAsJsonObject("error");
                return new Result("API 错误: " + e.get("message").getAsString(), "");
            }
            JsonArray choices = o.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject c0 = choices.get(0).getAsJsonObject();
                JsonObject m = c0.getAsJsonObject("message");
                String content = m.has("content") ? m.get("content").getAsString() : "";
                String reasoning = m.has("reasoning_content") ? m.get("reasoning_content").getAsString() : "";
                return new Result(content.trim(), reasoning.trim());
            }
        } catch (Exception e) {
            return new Result("解析失败: " + e.getMessage(), "");
        }
        return new Result("未识别到结果", "");
    }
}
