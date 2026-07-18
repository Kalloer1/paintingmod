package com.example.paintingmod.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side AI settings. Stored as JSON (not in the NeoForge config system) so the
 * in-game settings screen can write values at runtime without a restart.
 *
 * API base is customisable and defaults to SiliconFlow (硅基流动). The token belongs to
 * the player and is kept locally only — never synced to a server or other players.
 */
public final class AiConfig {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("paintingmod/ai.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String DEFAULT_BASE = "https://api.siliconflow.cn/v1/";
    public static final String DEFAULT_MODEL = "Qwen/Qwen2.5-VL-7B-Instruct";

    public String apiBase = DEFAULT_BASE;
    public String apiToken = "";
    public String model = DEFAULT_MODEL;

    /** GLM 系模型开启「深度思考」后，推理过程会出现在响应的 reasoning_content 字段，
     *  抓出来显示给玩家。非 GLM 模型（如硅基流动 Qwen）忽略此开关。 */
    public boolean think = true;
    /** 生成温度，0(确定性)~1(发散)，默认 0.5。 */
    public double temperature = 0.5;
    /** 单次回复最大 token，需覆盖「思考 + 6 行结果」，默认 1500。 */
    public int maxTokens = 1500;

    private static volatile AiConfig INSTANCE = load();

    public static AiConfig get() { return INSTANCE; }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            synchronized (INSTANCE) {
                Files.writeString(FILE, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static AiConfig load() {
        try {
            if (Files.exists(FILE)) {
                AiConfig c = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), AiConfig.class);
                if (c != null) {
                    if (c.apiBase == null || c.apiBase.isBlank()) c.apiBase = DEFAULT_BASE;
                    if (c.model == null || c.model.isBlank()) c.model = DEFAULT_MODEL;
                    return c;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new AiConfig();
    }

    public boolean hasToken() {
        return apiToken != null && !apiToken.isBlank();
    }
}
