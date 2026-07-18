package com.example.paintingmod.ai;

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

        CompletableFuture.supplyAsync(() -> {
            try {
                String b64 = encodePng(w, h, px);
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

    private static String encodePng(int w, int h, int[] px) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] rgb = new int[w * h];
        for (int i = 0; i < rgb.length; i++) {
            int v = px[i];
            rgb[i] = (v < 0) ? Palette.PAPER : v;
        }
        img.setRGB(0, 0, w, h, rgb, 0, w);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private static String buildBody(AiConfig cfg, String b64) {
        // 1) base instructions — hot-editable in config/paintingmod/appraisal_prompt.txt
        StringBuilder prompt = new StringBuilder();
        prompt.append(ModConfigFiles.readAppraisalPrompt());

        // 2) inject the authoritative allowlist so cold / unpopular entries are just as
        //    reachable as popular ones (the model is told the exact vocabulary it may use)
        prompt.append("\n\n【可发放清单】 (只可从中选择，不要编造清单外的东西)\n");
        prompt.append("· 物品: ").append(String.join(" ", RewardTable.itemIds())).append("\n");
        prompt.append("· 生物: ").append(String.join(" ", RewardTable.entityIds())).append("\n");
        prompt.append("· 状态效果: ").append(String.join(" ", RewardTable.effectIds())).append("\n");
        prompt.append("· 指令(关键词→命令): ").append(String.join("  ", RewardTable.commandKeys())).append("\n");

        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", prompt.toString());

        JsonObject imgPart = new JsonObject();
        imgPart.addProperty("type", "image_url");
        JsonObject imgUrl = new JsonObject();
        imgUrl.addProperty("url", "data:image/png;base64," + b64);
        imgPart.add("image_url", imgUrl);

        JsonArray content = new JsonArray();
        content.add(textPart);
        content.add(imgPart);

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.add("content", content);

        JsonArray messages = new JsonArray();
        messages.add(message);

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
