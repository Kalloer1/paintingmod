package com.example.paintingmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.example.paintingmod.ai.RewardTable;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads / writes the two player-editable text config files living in
 * {@code <game>/config/paintingmod/}:
 *   - appraisal_prompt.txt : the instructions sent to the vision model (hot-reloaded).
 *   - rewards.json         : the table of everything magic appraisal may grant
 *                             (items / entities / effects / commands).
 *
 * Both are created with sensible defaults the first time the mod runs, so the game
 * works out of the box and the player can tweak either file without restarting
 * (the prompt is re-read on every appraisal; rewards.json on every appraisal too).
 */
public final class ModConfigFiles {
    private ModConfigFiles() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("paintingmod"); }
    public static Path appraisalPromptPath() { return dir().resolve("appraisal_prompt.txt"); }
    public static Path rewardsPath() { return dir().resolve("rewards.json"); }

    // ---- defaults (written once if the file is missing) ----

    /** Bump this whenever DEFAULT_PROMPT changes so an old on-disk copy is overwritten. */
    public static final int PROMPT_VERSION = 2;
    private static final String PROMPT_VERSION_MARKER = "// prompt-version: " + PROMPT_VERSION;

    public static final String DEFAULT_PROMPT =
            PROMPT_VERSION_MARKER + "\n" +
            "You are the \"Pixel Canvas Appraiser\" for Minecraft Java Edition 1.21.1.\n" +
            "\n" +
            "Your ONLY job: look at the player's pixel painting on the canvas, decide what in-game " +
            "reward a Minecraft player most likely wanted to obtain by drawing this, then pick the " +
            "single best match from the ALLOWED REWARD VOCABULARY below.\n" +
            "\n" +
            "CORE PRINCIPLES\n" +
            "- Judge ONLY from the picture itself and the vocabulary. Do NOT apply any \"classic mapping\" " +
            "or outside experience unless it is one of the corrections below.\n" +
            "- Use COLOUR as a strong signal.\n" +
            "- Items have the HIGHEST priority: if the picture is clearly an item/block and a valid id " +
            "is given in the allowed items list, grant the item.\n" +
            "- Common misreads to correct:\n" +
            "  · green block with black hollow eyes + frown = creeper face -> entity minecraft:creeper\n" +
            "  · tall black figure with purple eyes = enderman -> entity minecraft:enderman\n" +
            "  · wings / glider = elytra -> item minecraft:elytra\n" +
            "  · white skull face = skeleton -> entity minecraft:skeleton\n" +
            "  · pink pig = pig -> entity minecraft:pig\n" +
            "  · villager / long nose = villager -> entity minecraft:villager\n" +
            "  · redstone torch = item minecraft:redstone_torch\n" +
            "\n" +
            "OUTPUT FORMAT (strictly follow)\n" +
            "1. Write a short human-readable appraisal in English (2-4 sentences: subject, colours, " +
            "what it most resembles).\n" +
            "2. Then output EXACTLY ONE machine-readable grant line, with no extra text around it:\n" +
            "   GRANT|<item|entity|effect|command>|<id or command>\n" +
            "   Examples:\n" +
            "     GRANT|item|minecraft:diamond\n" +
            "     GRANT|entity|minecraft:creeper\n" +
            "     GRANT|effect|minecraft:night_vision\n" +
            "     GRANT|command|time set day\n" +
            "3. Do NOT output JSON, Markdown, code fences, or any extra explanation.\n" +
            "\n" +
            "RULES\n" +
            "- The grant id MUST come from the ALLOWED REWARD VOCABULARY. Never invent ids outside it.\n" +
            "- If the picture clearly matches a mod item, output namespace:id; the server validates it.\n" +
            "- If it matches nothing, output: GRANT|item|minecraft:paper (the system re-rolls a random reward).\n" +
            "- Item priority beats entity/effect/command when a valid item id is present.\n" +
            "Only output the appraisal sentence and the single GRANT line.";

    public static final String DEFAULT_REWARDS_JSON = GSON.toJson(buildDefaultRewards());

    // ---- reading ----

    public static String readAppraisalPrompt() {
        Path p = appraisalPromptPath();
        if (Files.exists(p)) {
            try {
                String s = Files.readString(p, StandardCharsets.UTF_8);
                // overwrite only when the on-disk version is stale (missing/old marker)
                if (!s.contains(PROMPT_VERSION_MARKER)) write(p, DEFAULT_PROMPT);
                else return s.isBlank() ? DEFAULT_PROMPT : s;
            } catch (IOException ignored) {}
        } else {
            write(p, DEFAULT_PROMPT);
        }
        return DEFAULT_PROMPT;
    }

    /** Local fallback for sparse sketches: map a dominant RGB to a reward grant string
     *  of the form "item|minecraft:xxx". Returns null when no colour is close enough. */
    public static String localColorReward(int r, int g, int b) {
        int bestD = Integer.MAX_VALUE;
        ColorReward best = null;
        for (ColorReward c : LOCAL_COLOR_REWARDS) {
            int d = (c.r - r) * (c.r - r) + (c.g - g) * (c.g - g) + (c.b - b) * (c.b - b);
            if (d < bestD) { bestD = d; best = c; }
        }
        return (best == null) ? null : best.type + "|" + best.id;
    }

    private record ColorReward(int r, int g, int b, String type, String id) {}
    private static final ColorReward[] LOCAL_COLOR_REWARDS = {
        new ColorReward(0xE0, 0x20, 0x20, "item", "minecraft:redstone"),
        new ColorReward(0x2E, 0xCC, 0x71, "item", "minecraft:slime_ball"),
        new ColorReward(0x34, 0x98, 0xDB, "item", "minecraft:diamond"),
        new ColorReward(0xF1, 0xC4, 0x0F, "item", "minecraft:gold_ingot"),
        new ColorReward(0x9B, 0x59, 0xB6, "item", "minecraft:amethyst_shard"),
        new ColorReward(0xE6, 0x7E, 0x22, "item", "minecraft:carrot"),
        new ColorReward(0xEC, 0xF0, 0xF1, "item", "minecraft:snowball"),
        new ColorReward(0x2C, 0x3E, 0x50, "item", "minecraft:coal"),
        new ColorReward(0x8E, 0x44, 0xAD, "effect", "minecraft:night_vision"),
    };

    public static JsonObject readRewards() {
        Path p = rewardsPath();
        if (!Files.exists(p)) {
            // 首次启动：自动把当前游戏注册表（原版 + 已加载模组）全量写入 rewards.json
            write(p, RewardTable.buildFullRewardsJson());
        }
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- writing ----

    private static void write(Path p, String content) {
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                w.write(content);
            }
        } catch (IOException ignored) {}
    }

    /** Build the default rewards table (used to seed rewards.json on first run).
     *  Curated but broad — common AND cold items are both included so the AI can reach
     *  unpopular entries instead of always picking the same few popular ones. */
    private static JsonObject buildDefaultRewards() {
        JsonObject root = new JsonObject();

        String[] items = {
            "minecraft:diamond", "minecraft:diamond_block", "minecraft:emerald", "minecraft:emerald_block",
            "minecraft:gold_ingot", "minecraft:gold_block", "minecraft:iron_ingot", "minecraft:iron_block",
            "minecraft:copper_ingot", "minecraft:copper_block", "minecraft:netherite_ingot", "minecraft:netherite_block",
            "minecraft:redstone", "minecraft:redstone_block", "minecraft:lapis_lazuli", "minecraft:lapis_block",
            "minecraft:coal", "minecraft:coal_block", "minecraft:amethyst_shard", "minecraft:amethyst_block",
            "minecraft:quartz", "minecraft:quartz_block", "minecraft:prismarine_shard", "minecraft:prismarine_crystals",
            "minecraft:slime_ball", "minecraft:slime_block", "minecraft:glowstone", "minecraft:glow_ink_sac",
            "minecraft:ender_pearl", "minecraft:eye_of_ender", "minecraft:blaze_rod", "minecraft:blaze_powder",
            "minecraft:ghast_tear", "minecraft:magma_cream", "minecraft:fermented_spider_eye", "minecraft:spider_eye",
            "minecraft:string", "minecraft:feather", "minecraft:leather", "minecraft:rabbit_hide",
            "minecraft:bone", "minecraft:gunpowder", "minecraft:flint", "minecraft:flint_and_steel",
            "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:tipped_arrow", "minecraft:fire_charge",
            "minecraft:apple", "minecraft:golden_apple", "minecraft:enchanted_golden_apple", "minecraft:carrot",
            "minecraft:golden_carrot", "minecraft:potato", "minecraft:baked_potato", "minecraft:beetroot",
            "minecraft:bread", "minecraft:cookie", "minecraft:cake", "minecraft:pumpkin_pie",
            "minecraft:melon_slice", "minecraft:glistering_melon_slice", "minecraft:sweet_berries", "minecraft:glow_berries",
            "minecraft:sugar", "minecraft:egg", "minecraft:honey_bottle", "minecraft:honeycomb",
            "minecraft:milk_bucket", "minecraft:water_bucket", "minecraft:lava_bucket", "minecraft:powder_snow_bucket",
            "minecraft:snowball", "minecraft:experience_bottle", "minecraft:book", "minecraft:enchanted_book",
            "minecraft:writable_book", "minecraft:paper", "minecraft:map", "minecraft:compass",
            "minecraft:clock", "minecraft:glass_bottle", "minecraft:ender_eye", "minecraft:nether_star",
            "minecraft:totem_of_undying", "minecraft:heart_of_the_sea", "minecraft:nautilus_shell", "minecraft:trident",
            "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:end_stone", "minecraft:end_portal_frame",
            "minecraft:netherrack", "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:basalt",
            "minecraft:blackstone", "minecraft:gilded_blackstone", "minecraft:ancient_debris", "minecraft:netherite_scrap",
            "minecraft:stone", "minecraft:smooth_stone", "minecraft:cobblestone",
            "minecraft:deepslate", "minecraft:calcite", "minecraft:tuff", "minecraft:dripstone_block",
            "minecraft:andesite", "minecraft:granite", "minecraft:diorite", "minecraft:terracotta",
            "minecraft:brick", "minecraft:nether_bricks", "minecraft:red_nether_bricks", "minecraft:mud_bricks",
            "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log", "minecraft:jungle_log",
            "minecraft:acacia_log", "minecraft:dark_oak_log", "minecraft:mangrove_log", "minecraft:crimson_stem",
            "minecraft:warped_stem", "minecraft:oak_planks", "minecraft:birch_planks", "minecraft:spruce_planks",
            "minecraft:oak_sapling", "minecraft:spruce_sapling", "minecraft:dark_oak_sapling", "minecraft:azalea",
            "minecraft:glass", "minecraft:glass_pane", "minecraft:stained_glass", "minecraft:tinted_glass",
            "minecraft:torch", "minecraft:soul_torch", "minecraft:lantern", "minecraft:soul_lantern",
            "minecraft:redstone_lamp", "minecraft:sea_lantern", "minecraft:ender_chest", "minecraft:chest",
            "minecraft:trapped_chest", "minecraft:barrel", "minecraft:shulker_box",
            "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:smithing_table", "minecraft:grindstone",
            "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:hopper",
            "minecraft:rail", "minecraft:powered_rail", "minecraft:detector_rail", "minecraft:activator_rail",
            "minecraft:minecart", "minecraft:chest_minecart", "minecraft:furnace_minecart", "minecraft:tnt_minecart",
            "minecraft:oak_door", "minecraft:iron_door", "minecraft:trapdoor", "minecraft:fence",
            "minecraft:oak_fence", "minecraft:nether_brick_fence", "minecraft:chain", "minecraft:bell",
            "minecraft:campfire", "minecraft:soul_campfire", "minecraft:lodestone",
            "minecraft:target", "minecraft:observer", "minecraft:piston", "minecraft:sticky_piston",
            "minecraft:dispenser", "minecraft:dropper", "minecraft:comparator", "minecraft:repeater",
            "minecraft:redstone_torch", "minecraft:lever", "minecraft:button", "minecraft:pressure_plate",
            "minecraft:daylight_detector", "minecraft:lightning_rod", "minecraft:pointed_dripstone", "minecraft:scaffolding",
            "minecraft:honey_block", "minecraft:hay_block", "minecraft:spectral_arrow", "minecraft:firework_rocket",
            "minecraft:firework_star", "minecraft:crossbow",
            "minecraft:bow", "minecraft:shield", "minecraft:iron_sword", "minecraft:diamond_sword",
            "minecraft:netherite_sword", "minecraft:golden_sword", "minecraft:wooden_sword", "minecraft:stone_sword",
            "minecraft:iron_pickaxe", "minecraft:diamond_pickaxe", "minecraft:netherite_pickaxe", "minecraft:golden_pickaxe",
            "minecraft:iron_axe", "minecraft:diamond_axe", "minecraft:netherite_axe", "minecraft:iron_shovel",
            "minecraft:diamond_shovel", "minecraft:netherite_shovel", "minecraft:iron_hoe", "minecraft:diamond_hoe",
            "minecraft:netherite_hoe", "minecraft:shears", "minecraft:fishing_rod", "minecraft:carrot_on_a_stick",
            "minecraft:armor_stand", "minecraft:item_frame", "minecraft:painting", "minecraft:music_disc_13",
            "minecraft:music_disc_cat", "minecraft:music_disc_blocks", "minecraft:music_disc_chirp", "minecraft:music_disc_far",
            "minecraft:music_disc_mall", "minecraft:music_disc_mellohi", "minecraft:music_disc_stal", "minecraft:music_disc_strad",
            "minecraft:music_disc_ward", "minecraft:music_disc_11", "minecraft:music_disc_wait", "minecraft:music_disc_5",
            "minecraft:music_disc_pigstep", "minecraft:music_disc_otherside", "minecraft:music_disc_relic", "minecraft:saddle",
            "minecraft:lead", "minecraft:name_tag", "minecraft:dragon_head",
            "minecraft:creeper_head", "minecraft:skeleton_skull", "minecraft:wither_skeleton_skull", "minecraft:zombie_head",
            "minecraft:player_head", "minecraft:dragon_breath", "minecraft:phantom_membrane", "minecraft:shulker_shell",
            "minecraft:turtle_scute", "minecraft:echo_shard", "minecraft:recovery_compass",
            "minecraft:amethyst_cluster", "minecraft:big_dripleaf", "minecraft:small_dripleaf", "minecraft:spore_blossom",
            "minecraft:azalea_leaves", "minecraft:flowering_azalea", "minecraft:moss_block", "minecraft:moss_carpet",
            "minecraft:glow_lichen", "minecraft:cave_vines", "minecraft:weeping_vines", "minecraft:twisting_vines",
            "minecraft:nether_wart", "minecraft:crimson_fungus", "minecraft:warped_fungus", "minecraft:crimson_roots",
            "minecraft:warped_roots", "minecraft:kelp", "minecraft:seagrass", "minecraft:lily_pad",
            "minecraft:brain_coral", "minecraft:brain_coral_block", "minecraft:tube_coral", "minecraft:fire_coral",
            "minecraft:horn_coral", "minecraft:bubble_coral", "minecraft:dead_bush", "minecraft:ice",
            "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:snow_block", "minecraft:snow",
            "minecraft:packed_mud", "minecraft:mud", "minecraft:suspicious_sand", "minecraft:suspicious_gravel",
            "minecraft:trial_key", "minecraft:ominous_trial_key", "minecraft:wind_charge", "minecraft:breeze_rod",
            "minecraft:sniffer_egg", "minecraft:turtle_egg", "minecraft:dragon_egg"
        };
        root.add("items", array(items));

        String[] entities = {
            "minecraft:chicken", "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:goat",
            "minecraft:rabbit", "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:llama",
            "minecraft:fox", "minecraft:wolf", "minecraft:cat", "minecraft:ocelot", "minecraft:parrot",
            "minecraft:villager", "minecraft:wandering_trader", "minecraft:axolotl", "minecraft:tadpole",
            "minecraft:bee", "minecraft:bat", "minecraft:mooshroom", "minecraft:sniffer", "minecraft:camel",
            "minecraft:zombie", "minecraft:zombie_villager", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_horse",
            "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton", "minecraft:spider", "minecraft:cave_spider",
            "minecraft:creeper", "minecraft:slime", "minecraft:magma_cube", "minecraft:zoglin", "minecraft:hoglin",
            "minecraft:piglin", "minecraft:piglin_brute", "minecraft:zombified_piglin", "minecraft:blaze", "minecraft:ghast",
            "minecraft:enderman", "minecraft:endermite", "minecraft:shulker", "minecraft:guardian", "minecraft:elder_guardian",
            "minecraft:witch", "minecraft:ravager", "minecraft:vex", "minecraft:evoker", "minecraft:vindicator",
            "minecraft:pillager", "minecraft:iron_golem", "minecraft:snow_golem", "minecraft:warden", "minecraft:allay",
            "minecraft:ender_dragon", "minecraft:wither", "minecraft:phantom", "minecraft:silverfish",
            "minecraft:strider", "minecraft:glow_squid", "minecraft:squid", "minecraft:dolphin",
            "minecraft:turtle", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
            "minecraft:bogged", "minecraft:breeze", "minecraft:armadillo"
        };
        root.add("entities", array(entities));

        String[] effects = {
            "night_vision", "resistance", "regeneration", "fire_resistance", "slow_falling", "speed",
            "strength", "jump_boost", "invisibility", "water_breathing", "absorption", "health_boost",
            "glowing", "luck", "poison", "weakness", "slowness", "hunger", "blindness", "levitation",
            "instant_health", "instant_damage", "saturation", "wither", "conduit_power", "dolphins_grace",
            "mining_fatigue", "nausea", "bad_omen", "hero_of_the_village", "darkness", "wind_charged",
            "infested", "oozing", "weaving", "raid_omen", "trial_omen"
        };
        root.add("effects", array(effects));

        com.google.gson.JsonArray commands = new com.google.gson.JsonArray();
        commands.add(cmd("晴天", "weather clear"));
        commands.add(cmd("下雨", "weather rain"));
        commands.add(cmd("雷暴", "weather thunder"));
        commands.add(cmd("白天", "time set day"));
        commands.add(cmd("黑夜", "time set night"));
        commands.add(cmd("正午", "time set noon"));
        commands.add(cmd("午夜", "time set midnight"));
        commands.add(cmd("加速时间", "time add 24000"));
        commands.add(cmd("平静", "weather clear"));
        commands.add(cmd("满月", "time set 16000"));
        root.add("commands", commands);

        return root;
    }

    private static com.google.gson.JsonArray array(String[] vals) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (String v : vals) a.add(v);
        return a;
    }

    private static com.google.gson.JsonObject cmd(String key, String command) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("key", key);
        o.addProperty("cmd", command);
        return o;
    }
}
