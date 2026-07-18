package com.example.paintingmod.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod configuration. Gameplay-affecting values live in the SERVER config (authoritative
 * on a server / integrated server); the CLIENT config only drives display defaults.
 */
public final class ModConfig {
    private ModConfig() {}

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // --- server (authoritative) ---
    public static final ModConfigSpec.BooleanValue consumeDye;
    public static final ModConfigSpec.IntValue dyeCostPerStroke;
    public static final ModConfigSpec.IntValue maxCanvasSize;
    public static final ModConfigSpec.BooleanValue universalDyeEnabled;
    public static final ModConfigSpec.IntValue defaultCanvasSize;
    public static final ModConfigSpec.IntValue effectDurationTicks;

    static {
        ModConfigSpec.Builder sb = new ModConfigSpec.Builder();
        consumeDye = sb.comment("Legacy: whether normal painting consumed dyes per stroke. Painting is now free; kept for compatibility.")
                .define("consumeDye", false);
        dyeCostPerStroke = sb.comment("Legacy dye cost. Unused now that painting is free.")
                .defineInRange("dyeCostPerStroke", 1, 1, 64);
        maxCanvasSize = sb.comment("Maximum canvas resolution (pixels per side) allowed on the server.")
                .defineInRange("maxCanvasSize", 128, 16, 128);
        universalDyeEnabled = sb.comment("Enable the universal dye item (free RGB mixing).")
                .define("universalDyeEnabled", true);
        defaultCanvasSize = sb.comment("Default canvas resolution for newly placed paint paper.")
                .defineInRange("defaultCanvasSize", 128, 16, 128);
        effectDurationTicks = sb.comment("Duration (in ticks) applied to status effects granted by magic appraisal. 6000 = 5 minutes.")
                .defineInRange("effectDurationTicks", 6000, 60, 60000);
        SERVER_SPEC = sb.build();

        ModConfigSpec.Builder cb = new ModConfigSpec.Builder();
        CLIENT_SPEC = cb.build();
    }

    public static void register(ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, SERVER_SPEC, "paintingmod-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, CLIENT_SPEC, "paintingmod-client.toml");
    }
}
