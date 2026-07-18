package com.example.paintingmod.client;

import com.example.paintingmod.canvas.CanvasBlock;
import com.example.paintingmod.canvas.CanvasBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the painting on the front face of the thin paint paper using a DynamicTexture
 * built from the pixel buffer. The paper rotates to match any placed face.
 */
@OnlyIn(Dist.CLIENT)
public class CanvasRenderer implements BlockEntityRenderer<CanvasBlockEntity> {
    private static final float INSET = 0.0625f;
    private static final float HT = 0.0625f;
    private static final float EPS = 0.002f;

    public CanvasRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CanvasBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (be.getWidth() <= 0 || be.getHeight() <= 0) return;
        ResourceLocation tex = CanvasTextureCache.getOrBuild(be);
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentCull(tex));

        Direction facing = Direction.SOUTH;
        BlockState bs = be.getBlockState();
        if (bs.hasProperty(CanvasBlock.FACING)) facing = bs.getValue(CanvasBlock.FACING);

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        switch (facing) {
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(0));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(270));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90));
        }
        pose.translate(-0.5f, -0.5f, -0.5f);
        drawFront(vc, pose, packedLight, packedOverlay);
        pose.popPose();
    }

    private void drawFront(VertexConsumer vc, PoseStack pose, int light, int overlay) {
        float z = 0.5f + HT + EPS;
        float[][] c = {
                {1 - INSET, INSET, z, 0, 1},
                {INSET, INSET, z, 1, 1},
                {INSET, 1 - INSET, z, 1, 0},
                {1 - INSET, 1 - INSET, z, 0, 0}
        };
        for (float[] p : c) {
            vc.addVertex(pose.last(), p[0], p[1], p[2])
                    .setColor(255, 255, 255, 255)
                    .setNormal(0, 0, 1)
                    .setUv(p[3], p[4])
                    .setLight(light)
                    .setOverlay(overlay);
        }
    }
}
