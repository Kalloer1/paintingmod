package com.example.paintingmod.ai;

import com.example.paintingmod.config.ModConfigFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for what magic appraisal may grant. The authoritative list is
 * the player-editable table in {@code config/paintingmod/rewards.json}
 * (items / entities / effects / commands). If that file is missing or empty the mod falls
 * back to the full game registry (minus a blacklist) so it still works out of the box.
 *
 * Because the same table is also injected into the AI prompt, cold / unpopular entries are
 * just as reachable as popular ones — the model is told the exact vocabulary it may use.
 */
public final class RewardTable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Entry {
        public final ResourceLocation id;
        public final String name;
        public final boolean isEntity;
        Entry(ResourceLocation id, String name, boolean isEntity) {
            this.id = id; this.name = name; this.isEntity = isEntity;
        }
    }

    private static final class CommandEntry {
        final String key; final String cmd;
        CommandEntry(String key, String cmd) { this.key = key; this.cmd = cmd; }
    }

    // registry fallback pools (always built, used when the JSON table is absent)
    private static final List<Entry> REG_ITEMS = new ArrayList<>();
    private static final List<Entry> REG_ENTITIES = new ArrayList<>();

    // JSON-driven allowlist (authoritative when jsonLoaded)
    private static boolean jsonLoaded = false;
    private static final Set<ResourceLocation> ALLOWED_ITEMS = new HashSet<>();
    private static final Set<ResourceLocation> ALLOWED_ENTITIES = new HashSet<>();
    private static final Set<ResourceLocation> ALLOWED_EFFECTS = new HashSet<>();
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>();
    private static final List<CommandEntry> COMMANDS = new ArrayList<>();

    private static final Set<ResourceLocation> ITEM_BLACKLIST = new HashSet<>();
    private static final Set<ResourceLocation> ENTITY_BLACKLIST = new HashSet<>();

    static {
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:command_block"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:chain_command_block"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:repeating_command_block"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:command_block_minecart"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:barrier"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:bedrock"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:structure_block"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:structure_void"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:jigsaw"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:light"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:debug_stick"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:cave_air"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:void_air"));
        ITEM_BLACKLIST.add(ResourceLocation.parse("minecraft:air"));

        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:player"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:experience_orb"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:item"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:lightning_bolt"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:falling_block"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:firework_rocket"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:fishing_bobber"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:leash_knot"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:item_frame"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:glow_item_frame"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:area_effect_cloud"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:ender_crystal"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:shulker_bullet"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:dragon_fireball"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:wither_skull"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:wind_charge"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:breeze_wind_charge"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:small_fireball"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:large_fireball"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:fireball"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:spectral_arrow"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:arrow"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:thrown_trident"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:llama_spit"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:evoker_fangs"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:marker"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:text_display"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:item_display"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:block_display"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:connection_node"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:tester"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:minecart"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:chest_minecart"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:furnace_minecart"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:tnt_minecart"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:hopper_minecart"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:spawner_minecart"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:command_block_minecart"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:tnt"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:snowball"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:ender_pearl"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:potion"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:experience_bottle"));
        ENTITY_BLACKLIST.add(ResourceLocation.parse("minecraft:egg"));

        ResourceLocation air = BuiltInRegistries.ITEM.getDefaultKey();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || id.equals(air) || item == Items.AIR) continue;
            if (ITEM_BLACKLIST.contains(id)) continue;
            REG_ITEMS.add(new Entry(id, Component.translatable(item.getDescriptionId()).getString(), false));
        }
        for (EntityType<?> et : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(et);
            if (id == null) continue;
            if (ENTITY_BLACKLIST.contains(id)) continue;
            REG_ENTITIES.add(new Entry(id, Component.translatable(et.getDescriptionId()).getString(), true));
        }

        loadJson();
    }

    /** Re-read config/paintingmod/rewards.json so edits take effect without a restart. */
    public static void reload() { loadJson(); }

    /**
     * Build a complete rewards.json from the live game registry — every item, entity and
     * status effect that is currently registered (vanilla AND any loaded mods), minus the
     * blacklist. This is what gets written to config/paintingmod/rewards.json the first time
     * the mod runs, so magic appraisal can grant anything the player has installed.
     */
    public static String buildFullRewardsJson() {
        JsonObject root = new JsonObject();

        JsonArray items = new JsonArray();
        for (Entry e : REG_ITEMS) items.add(e.id.toString());
        root.add("items", items);

        JsonArray entities = new JsonArray();
        for (Entry e : REG_ENTITIES) entities.add(e.id.toString());
        root.add("entities", entities);

        JsonArray effects = new JsonArray();
        for (MobEffect me : BuiltInRegistries.MOB_EFFECT)
            effects.add(BuiltInRegistries.MOB_EFFECT.getKey(me).toString());
        root.add("effects", effects);

        JsonArray cmds = new JsonArray();
        addCmd(cmds, "白天", "time set day");
        addCmd(cmds, "黑夜", "time set night");
        addCmd(cmds, "下雨", "weather rain");
        addCmd(cmds, "晴天", "weather clear");
        addCmd(cmds, "雷暴", "weather thunder");
        root.add("commands", cmds);

        return GSON.toJson(root);
    }

    private static void addCmd(JsonArray a, String key, String cmd) {
        JsonObject o = new JsonObject();
        o.addProperty("key", key);
        o.addProperty("cmd", cmd);
        a.add(o);
    }

    private static void loadJson() {
        JsonObject o = ModConfigFiles.readRewards();
        if (o == null) { jsonLoaded = false; return; }
        jsonLoaded = true;
        if (o.has("items") && o.get("items").isJsonArray()) {
            for (var e : o.getAsJsonArray("items")) {
                ResourceLocation rl = ResourceLocation.tryParse(e.getAsString());
                if (rl != null && !ITEM_BLACKLIST.contains(rl) && BuiltInRegistries.ITEM.containsKey(rl))
                    ALLOWED_ITEMS.add(rl);
            }
        }
        if (o.has("entities") && o.get("entities").isJsonArray()) {
            for (var e : o.getAsJsonArray("entities")) {
                ResourceLocation rl = ResourceLocation.tryParse(e.getAsString());
                if (rl != null && !ENTITY_BLACKLIST.contains(rl) && BuiltInRegistries.ENTITY_TYPE.containsKey(rl))
                    ALLOWED_ENTITIES.add(rl);
            }
        }
        if (o.has("effects") && o.get("effects").isJsonArray()) {
            for (var e : o.getAsJsonArray("effects")) {
                ResourceLocation rl = ResourceLocation.tryParse(e.getAsString());
                if (rl != null && BuiltInRegistries.MOB_EFFECT.containsKey(rl))
                    ALLOWED_EFFECTS.add(rl);
            }
        }
        if (o.has("commands") && o.get("commands").isJsonArray()) {
            for (var e : o.getAsJsonArray("commands")) {
                if (!e.isJsonObject()) continue;
                JsonObject c = e.getAsJsonObject();
                String key = c.has("key") ? c.get("key").getAsString() : "";
                String cmd = c.has("cmd") ? c.get("cmd").getAsString() : "";
                if (!key.isEmpty() && !cmd.isEmpty()) {
                    COMMANDS.add(new CommandEntry(key, cmd));
                    ALLOWED_COMMANDS.add(cmd);
                }
            }
        }
    }

    // ---- validation (server + client) ----

    public static boolean isItemAllowed(ResourceLocation id) {
        if (id == null) return false;
        if (ITEM_BLACKLIST.contains(id)) return false;
        if (jsonLoaded) return ALLOWED_ITEMS.contains(id);
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    public static boolean isEntityAllowed(ResourceLocation id) {
        if (id == null) return false;
        if (ENTITY_BLACKLIST.contains(id)) return false;
        if (jsonLoaded) return ALLOWED_ENTITIES.contains(id);
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    public static boolean isEffectAllowed(ResourceLocation id) {
        if (id == null) return false;
        if (!BuiltInRegistries.MOB_EFFECT.containsKey(id)) return false;
        if (jsonLoaded) return ALLOWED_EFFECTS.contains(id);
        return true;
    }

    public static boolean isCommandAllowed(String cmd) {
        if (cmd == null) return false;
        if (!jsonLoaded) return false;
        return ALLOWED_COMMANDS.contains(cmd);
    }

    /** Client-side: resolve a free-text "指令：<...>" line to one of the allow-listed commands. */
    public static String findCommand(String text) {
        if (text == null || !jsonLoaded) return null;
        String t = text.trim().toLowerCase(Locale.ROOT);
        for (CommandEntry c : COMMANDS) {
            if (t.contains(c.key.toLowerCase(Locale.ROOT))
                    || t.contains(c.cmd.toLowerCase(Locale.ROOT)))
                return c.cmd;
        }
        return null;
    }

    // ---- name resolution ----

    public static Entry findById(ResourceLocation id) {
        if (id == null) return null;
        for (Entry e : REG_ITEMS) if (e.id.equals(id)) return e;
        for (Entry e : REG_ENTITIES) if (e.id.equals(id)) return e;
        if (BuiltInRegistries.ITEM.containsKey(id))
            return new Entry(id, Component.translatable(BuiltInRegistries.ITEM.get(id).getDescriptionId()).getString(), false);
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id))
            return new Entry(id, Component.translatable(BuiltInRegistries.ENTITY_TYPE.get(id).getDescriptionId()).getString(), true);
        return null;
    }

    public static String effectName(ResourceLocation id) {
        MobEffect me = BuiltInRegistries.MOB_EFFECT.get(id);
        return me != null ? me.getDisplayName().getString() : id.toString();
    }

    // ---- random fallback (never returns null) ----

    private static final java.util.Random RANDOM = new java.util.Random();

    public static Entry random() {
        boolean entity = RANDOM.nextInt(5) == 0;
        List<Entry> pool = entity ? entityPool() : itemPool();
        if (pool.isEmpty()) pool = itemPool().isEmpty() ? entityPool() : itemPool();
        if (pool.isEmpty()) pool = REG_ITEMS.isEmpty() ? REG_ENTITIES : REG_ITEMS;
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    public static ResourceLocation randomEffect() {
        List<ResourceLocation> pool = effectPool();
        if (pool.isEmpty()) {
            for (MobEffect me : BuiltInRegistries.MOB_EFFECT)
                pool.add(BuiltInRegistries.MOB_EFFECT.getKey(me));
        }
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    private static List<Entry> itemPool() {
        if (jsonLoaded && !ALLOWED_ITEMS.isEmpty()) {
            List<Entry> out = new ArrayList<>();
            for (ResourceLocation id : ALLOWED_ITEMS) out.add(entryFromId(id, false));
            return out;
        }
        return REG_ITEMS;
    }

    private static List<Entry> entityPool() {
        if (jsonLoaded && !ALLOWED_ENTITIES.isEmpty()) {
            List<Entry> out = new ArrayList<>();
            for (ResourceLocation id : ALLOWED_ENTITIES) out.add(entryFromId(id, true));
            return out;
        }
        return REG_ENTITIES;
    }

    private static List<ResourceLocation> effectPool() {
        List<ResourceLocation> out = new ArrayList<>(ALLOWED_EFFECTS);
        return out;
    }

    private static Entry entryFromId(ResourceLocation id, boolean isEntity) {
        if (isEntity && BuiltInRegistries.ENTITY_TYPE.containsKey(id))
            return new Entry(id, Component.translatable(BuiltInRegistries.ENTITY_TYPE.get(id).getDescriptionId()).getString(), true);
        if (BuiltInRegistries.ITEM.containsKey(id))
            return new Entry(id, Component.translatable(BuiltInRegistries.ITEM.get(id).getDescriptionId()).getString(), false);
        return new Entry(id, id.toString(), isEntity);
    }

    // ---- vocabulary for prompt injection (client) ----

    public static List<String> itemIds() {
        List<String> out = new ArrayList<>();
        if (jsonLoaded && !ALLOWED_ITEMS.isEmpty()) for (ResourceLocation id : ALLOWED_ITEMS) out.add(id.toString());
        else for (Entry e : REG_ITEMS) out.add(e.id.toString());
        return out;
    }

    public static List<String> entityIds() {
        List<String> out = new ArrayList<>();
        if (jsonLoaded && !ALLOWED_ENTITIES.isEmpty()) for (ResourceLocation id : ALLOWED_ENTITIES) out.add(id.toString());
        else for (Entry e : REG_ENTITIES) out.add(e.id.toString());
        return out;
    }

    /** Vanilla-only ids for prompt injection — keeps the vision prompt from ballooning to
     *  tens of thousands of tokens on modded installs. Mod items are still grantable (the
     *  full registry lives in rewards.json); the model just won't see them listed. */
    public static List<String> itemIdsVanilla() {
        List<String> out = new ArrayList<>();
        if (jsonLoaded && !ALLOWED_ITEMS.isEmpty())
            for (ResourceLocation id : ALLOWED_ITEMS) if (id.getNamespace().equals("minecraft")) out.add(id.toString());
        else
            for (Entry e : REG_ITEMS) if (e.id.getNamespace().equals("minecraft")) out.add(e.id.toString());
        return out;
    }

    public static List<String> entityIdsVanilla() {
        List<String> out = new ArrayList<>();
        if (jsonLoaded && !ALLOWED_ENTITIES.isEmpty())
            for (ResourceLocation id : ALLOWED_ENTITIES) if (id.getNamespace().equals("minecraft")) out.add(id.toString());
        else
            for (Entry e : REG_ENTITIES) if (e.id.getNamespace().equals("minecraft")) out.add(e.id.toString());
        return out;
    }

    public static List<String> effectIds() {
        List<String> out = new ArrayList<>();
        if (jsonLoaded && !ALLOWED_EFFECTS.isEmpty()) for (ResourceLocation id : ALLOWED_EFFECTS) out.add(id.getPath());
        else for (MobEffect me : BuiltInRegistries.MOB_EFFECT) out.add(BuiltInRegistries.MOB_EFFECT.getKey(me).getPath());
        return out;
    }

    public static List<String> commandKeys() {
        List<String> out = new ArrayList<>();
        for (CommandEntry c : COMMANDS) out.add(c.key + "→" + c.cmd);
        return out;
    }
}
