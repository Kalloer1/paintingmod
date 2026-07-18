package com.example.paintingmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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

    public static final String DEFAULT_PROMPT =
            "你是《我的世界 Java版 1.21.1 AI绘画奖励转换器》。\n" +
            "\n" +
            "你的唯一任务：\n" +
            "分析玩家在画板上的图案，推测玩家最想获得的 Minecraft 奖励，然后从下方【可发放清单】(来自 rewards.json) 中选择一个合法奖励。\n" +
            "\n" +
            "你不是现实图片识别 AI。不要回答“现实世界是什么”“这是什么真实物体”之类的问题。\n" +
            "\n" +
            "你的思考方式必须是：\n" +
            "“一个 Minecraft 玩家画了这个东西，他最可能想获得什么？”\n" +
            "始终站在 MC 玩家视角，把图案映射成游戏内能得到的奖励。\n" +
            "\n" +
            "请严格按以下格式输出 7 行，不要输出任何额外解释文字：\n" +
            "描述：<用 2-4 句中文描述画面主体、构图与颜色，并说明它最像游戏里的什么>\n" +
            "思考过程：<2-3 句，说明你如何从画面联想到这个奖励，例如：画的是红石火把→玩家多半想要红石类→给 redstone_torch>\n" +
            "方向：物品 <w1> 生物 <w2> 状态 <w3>\n" +
            "物品：<若画的是清单内物品/方块/结构，给出 minecraft:<id>，否则写 无>\n" +
            "生物：<若画的是清单内生物，给出 minecraft:<id>，否则写 无>\n" +
            "状态：<若画的是某种 buff/符号，给出效果英文名或中文名，否则写 无>\n" +
            "天气：<若画的是天气/雷电相关，写 晴|雨|雷暴|闪电，否则写 无>\n" +
            "指令：<若画意是切换某种游戏状态(如白天/黑夜/晴天)，给出清单内关键词，否则写 无>\n" +
            "\n" +
            "规则：\n" +
            "1. 三个方向权重 w1 w2 w3 用 0~1 之间的小数，反映「这幅画更偏向哪一种结果」。\n" +
            "2. 物品/生物/指令必须来自下方【可发放清单】，绝不可编造清单外的内容（尤其不可出现 command_block 等创造/指令专用方块，也不可出现现实物品）。\n" +
            "3. 状态效果举例：眼睛→night_vision；盾牌→resistance；心脏/红心→regeneration；火焰/火→fire_resistance；羽毛/翅膀→slow_falling；药水→选一个合适的 buff。\n" +
            "4. 天气类优先级最高：闪电/雷电符号→天气 闪电；太阳/晴天→天气 晴；乌云下雨→天气 雨；暴风雨雷电→天气 雷暴。\n" +
            "5. 指令类：若画意是切换某种游戏状态(白天/黑夜/晴天/加速时间等)，在「指令」行给出【可发放清单】里对应的关键词。\n" +
            "6. 若完全不像任何东西，各方向都写 无，系统会兜底发一个清单内的物品。\n" +
            "只输出那 7 行，不要任何额外文字。";

    public static final String DEFAULT_REWARDS_JSON = GSON.toJson(buildDefaultRewards());

    // ---- reading ----

    public static String readAppraisalPrompt() {
        Path p = appraisalPromptPath();
        if (!Files.exists(p)) write(p, DEFAULT_PROMPT);
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String s = new java.io.BufferedReader(r).lines().reduce((a, b) -> a + "\n" + b).orElse("");
            return s.isBlank() ? DEFAULT_PROMPT : s;
        } catch (IOException e) {
            return DEFAULT_PROMPT;
        }
    }

    public static JsonObject readRewards() {
        Path p = rewardsPath();
        if (!Files.exists(p)) write(p, DEFAULT_REWARDS_JSON);
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
            "minecraft:music_disc_pigstep", "minecraft:music_disc_othersid", "minecraft:music_disc_relic", "minecraft:saddle",
            "minecraft:lead", "minecraft:name_tag", "minecraft:dragon_head",
            "minecraft:creeper_head", "minecraft:skeleton_skull", "minecraft:wither_skeleton_skull", "minecraft:zombie_head",
            "minecraft:player_head", "minecraft:dragon_breath", "minecraft:phantom_membrane", "minecraft:shulker_shell",
            "minecraft:turtle_scute", "minecraft:scute", "minecraft:echo_shard", "minecraft:recovery_compass",
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
            "minecraft:ender_man", "minecraft:endermite", "minecraft:shulker", "minecraft:guardian", "minecraft:elder_guardian",
            "minecraft:witch", "minecraft:ravager", "minecraft:vex", "minecraft:evoker", "minecraft:vindicator",
            "minecraft:pillager", "minecraft:iron_golem", "minecraft:snow_golem", "minecraft:warden", "minecraft:allay",
            "minecraft:ender_dragon", "minecraft:wither", "minecraft:phantom", "minecraft:silverfish",
            "minecraft:strider", "minecraft:axolotl", "minecraft:glow_squid", "minecraft:squid", "minecraft:dolphin",
            "minecraft:turtle", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
            "minecraft:creaking", "minecraft:bogged", "minecraft:breeze", "minecraft:armadillo"
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
