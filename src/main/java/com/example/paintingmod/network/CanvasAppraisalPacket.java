package com.example.paintingmod.network;

import com.example.paintingmod.ai.RewardTable;
import com.example.paintingmod.canvas.CanvasBlockEntity;
import com.mojang.brigadier.ParseResults;
import com.example.paintingmod.canvas.PaintingData;
import com.example.paintingmod.config.ModConfig;
import com.example.paintingmod.registry.ModItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Client -> Server: the player finished a magic appraisal. The client resolved the AI text
 * into a structured effect (item / creature / status-effect / clear-rain-thunder weather /
 * lightning), but the server is authoritative: it re-validates any id, always falls back to a
 * real grantable reward, consumes one magic dye, performs the effect, stores the artwork into
 * a "magic canvas" (魔法画稿) item, and clears the wall canvas. A blank canvas is never sent
 * here, so the magic dye is only spent on a real appraisal.
 *
 * Effect types: 0=item, 1=entity, 2=weather clear, 3=weather rain, 4=weather thunder,
 *               5=lightning, 6=status effect (potion) applied to the player.
 */
public record CanvasAppraisalPacket(Optional<BlockPos> pos, byte effectType, String effectId,
                                    byte amplifier, String desc, int width, int height, int[] pixels)
        implements CustomPacketPayload {

    public static final byte EFFECT_ITEM = 0;
    public static final byte EFFECT_ENTITY = 1;
    public static final byte EFFECT_WEATHER_CLEAR = 2;
    public static final byte EFFECT_WEATHER_RAIN = 3;
    public static final byte EFFECT_WEATHER_THUNDER = 4;
    public static final byte EFFECT_LIGHTNING = 5;
    public static final byte EFFECT_POTION = 6;
    public static final byte EFFECT_COMMAND = 7;

    public static final Type<CanvasAppraisalPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("paintingmod", "canvas_appraisal"));

    public static final StreamCodec<ByteBuf, CanvasAppraisalPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = (FriendlyByteBuf) buf;
                f.writeBoolean(p.pos().isPresent());
                p.pos().ifPresent(f::writeBlockPos);
                f.writeByte(p.effectType());
                f.writeUtf(p.effectId() == null ? "" : p.effectId());
                f.writeByte(p.amplifier());
                f.writeUtf(p.desc() == null ? "" : p.desc());
                f.writeVarInt(p.width());
                f.writeVarInt(p.height());
                f.writeVarIntArray(p.pixels());
            },
            buf -> {
                FriendlyByteBuf f = (FriendlyByteBuf) buf;
                BlockPos bp = f.readBoolean() ? f.readBlockPos() : null;
                Optional<BlockPos> pos = Optional.ofNullable(bp);
                byte type = f.readByte();
                String id = f.readUtf();
                byte amp = f.readByte();
                String desc = f.readUtf();
                int w = f.readVarInt();
                int h = f.readVarInt();
                int[] px = f.readVarIntArray();
                return new CanvasAppraisalPacket(pos, type, id, amp, desc, w, h, px);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CanvasAppraisalPacket p, IPayloadContext ctx) {
        if (!ctx.flow().isServerbound()) return;
        ctx.enqueueWork(() -> {
            RewardTable.reload(); // pick up any edits to rewards.json
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;
            Level level = player.level();

            // 1) perform the effect the AI (client) described, server-authoritative.
            String granted = null;
            String rewardName = "?";
            ResourceLocation rl = (p.effectId() != null && !p.effectId().isBlank())
                    ? ResourceLocation.tryParse(p.effectId()) : null;

            switch (p.effectType()) {
                case EFFECT_ITEM -> {
                    RewardTable.Entry e = (rl != null) ? RewardTable.findById(rl) : null;
                    if (e == null) e = RewardTable.random();
                    if (RewardTable.isItemAllowed(e.id)) {
                        Item item = BuiltInRegistries.ITEM.get(e.id);
                        if (item != null && item != Items.AIR) {
                            ItemStack stack = new ItemStack(item, 1);
                            if (!player.getInventory().add(stack)) player.drop(stack, false);
                            granted = e.name; rewardName = e.name;
                        }
                    }
                }
                case EFFECT_ENTITY -> {
                    RewardTable.Entry e = (rl != null) ? RewardTable.findById(rl) : null;
                    if (e == null) e = RewardTable.random();
                    if (e.isEntity && RewardTable.isEntityAllowed(e.id)) {
                        EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.get(e.id);
                        Entity ent = (et != null) ? et.create(level) : null;
                        if (ent != null) {
                            ent.moveTo(player.getX(), player.getY() + 0.5, player.getZ(), player.getYRot(), 0);
                            level.addFreshEntity(ent);
                            granted = e.name; rewardName = e.name;
                        }
                    }
                }
                case EFFECT_POTION -> {
                    if (rl != null) {
                        var opt = BuiltInRegistries.MOB_EFFECT.getOptional(rl);
                        if (opt.isPresent()) {
                            MobEffect me = opt.get();
                            int dur = ModConfig.effectDurationTicks.get();
                            int amp = Math.max(0, Math.min(4, p.amplifier()));
                            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(me);
                            player.addEffect(new MobEffectInstance(holder, dur, amp, false, true));
                            granted = me.getDisplayName().getString();
                            rewardName = granted;
                        }
                    }
                }
                case EFFECT_WEATHER_CLEAR -> { setWeather(player, "clear"); rewardName = "晴天"; granted = "晴朗的天气"; }
                case EFFECT_WEATHER_RAIN -> { setWeather(player, "rain"); rewardName = "下雨"; granted = "降雨天气"; }
                case EFFECT_WEATHER_THUNDER -> { setWeather(player, "thunder"); rewardName = "雷暴"; granted = "雷暴天气"; }
                case EFFECT_LIGHTNING -> { strikeLightning(level, player); rewardName = "闪电"; granted = "一道闪电"; }
                case EFFECT_COMMAND -> {
                    if (p.effectId() != null && RewardTable.isCommandAllowed(p.effectId())) {
                        runCommand(player, p.effectId());
                        rewardName = p.effectId(); granted = "指令：" + p.effectId();
                    }
                }
                default -> { }
            }
            // last-resort so a spent canvas always yields *something* real.
            if (granted == null) {
                ItemStack paper = new ItemStack(Items.PAPER, 1);
                if (!player.getInventory().add(paper)) player.drop(paper, false);
                granted = "纸"; rewardName = "纸";
            }

            // 3) store the artwork into a magic-canvas stub item.
            ItemStack stub = new ItemStack(ModItems.MAGIC_CANVAS_STUB.get());
            CompoundTag cd = new CompoundTag();
            cd.put("Painting", new PaintingData(p.width(), p.height(), p.pixels()).save());
            cd.putString("Label", p.desc() != null ? p.desc() : "");
            cd.putString("RewardId", rewardName);
            cd.putString("RewardName", rewardName);
            stub.set(DataComponents.CUSTOM_DATA, CustomData.of(cd));
            if (!player.getInventory().add(stub)) player.drop(stub, false);

            // 4) clear the wall canvas (held paper is cleared on the client side).
            if (p.pos().isPresent()) {
                var be = player.level().getBlockEntity(p.pos().get());
                if (be instanceof CanvasBlockEntity cbe) cbe.clearPixels();
            }

            player.sendSystemMessage(Component.literal("§d魔法鉴定完成§r：识别为 §e" + rewardName
                    + "§r，已赐予 §a" + granted + "§r，画作已封存于「魔法画稿」。"));
        });
    }

    private static void setWeather(ServerPlayer player, String kind) {
        // Run the vanilla /weather command as the player so it applies to their dimension.
        String command = "weather " + kind;
        CommandSourceStack src = player.createCommandSourceStack().withPermission(4);
        ParseResults<CommandSourceStack> pr =
                player.getServer().getCommands().getDispatcher().parse(command, src);
        player.getServer().getCommands().performCommand(pr, command);
    }

    private static void strikeLightning(Level level, ServerPlayer player) {
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.moveTo(player.getX(), player.getY(), player.getZ());
        level.addFreshEntity(bolt);
    }

    /** Run a player-sourced command. The command string is already validated against the
     *  rewards table (only allow-listed commands may run) so this can't be abused. */
    private static void runCommand(ServerPlayer player, String command) {
        CommandSourceStack src = player.createCommandSourceStack().withPermission(4);
        ParseResults<CommandSourceStack> pr =
                player.getServer().getCommands().getDispatcher().parse(command, src);
        player.getServer().getCommands().performCommand(pr, command);
    }
}
