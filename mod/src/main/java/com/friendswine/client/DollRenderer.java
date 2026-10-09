package com.friendswine.client;

import com.friendswine.DollBlock;
import com.friendswine.DollBlockEntity;
import com.friendswine.JellyAnimation;
import com.friendswine.ClientConfig;
import com.friendswine.EntityAnimation;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class DollRenderer implements BlockEntityRenderer<DollBlockEntity> {
    private static final ResourceLocation MESH = ResourceLocation.fromNamespaceAndPath("friendswine", "models/entity/doll.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("friendswine", "textures/entity/doll.png");
    private static float[][] vertices;

    public DollRenderer(BlockEntityRendererProvider.Context context) {}

    public static void invalidateMesh() { vertices = null; }

    private static float[][] mesh() {
        if (vertices == null) {
            try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(MESH)
                    .openAsReader()) {
                JsonArray data = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("vertices");
                if (data.size() == 0 || data.size() % 3 != 0) throw new IOException("Invalid doll triangle mesh");
                float[][] result = new float[data.size()][8];
                for (int i = 0; i < data.size(); i++) {
                    JsonArray point = data.get(i).getAsJsonArray();
                    if (point.size() != 8) throw new IOException("Invalid doll vertex");
                    for (int j = 0; j < 8; j++) {
                        result[i][j] = point.get(j).getAsFloat();
                        if (!Float.isFinite(result[i][j])) throw new IOException("Non-finite doll vertex");
                    }
                }
                vertices = result;
            } catch (IOException ex) {
                throw new UncheckedIOException("Cannot load friendswine doll mesh", ex);
            }
        }
        return vertices;
    }

    @Override
    public void render(DollBlockEntity doll, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - doll.getBlockState().getValue(DollBlock.FACING).toYRot()));
        if (doll.isPlaying() && doll.getLevel() != null) {
            pose.translate(0, 0, 1.0 / 16);
            applyAnimation(pose, doll, partialTick);
            pose.translate(0, 0, -1.0 / 16);
        }
        draw(pose, buffers, light, overlay);
        pose.popPose();
    }

    public static void applyAnimation(PoseStack pose, DollBlockEntity doll, float partialTick) {
        long time = doll.getLevel().getGameTime();
        double seconds = (doll.getElapsedTicks(time) + partialTick) / 20.0;
        double rotationSeconds = (doll.getRotationElapsedTicks(time) + partialTick) / 20.0;
        applyAnimation(pose, new EntityAnimation(seconds, rotationSeconds, doll.isRotating(), true));
    }

    public static void applyAnimation(PoseStack pose, EntityAnimation animation) {
        float squash = animation.squash();
        if (animation.rotating()) {
            pose.mulPose(Axis.YP.rotationDegrees(JellyAnimation.angle(animation.rotationSeconds() * ClientConfig.ROTATION_SPEED.get().doubleValue())));
        }
        pose.scale(JellyAnimation.widthScale(squash, ClientConfig.WIDTH.get()), ClientConfig.heightScale(squash), 1);
    }

    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose transform = pose.last();
        float[][] points = mesh();
        for (int i = 0; i < points.length; i += 3) {
            vertex(consumer, transform, points[i], light, overlay);
            vertex(consumer, transform, points[i + 1], light, overlay);
            vertex(consumer, transform, points[i + 2], light, overlay);
            // Vanilla's entity buffer uses quads; repeat the last vertex for a triangle.
            vertex(consumer, transform, points[i + 2], light, overlay);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float[] p, int light, int overlay) {
        out.addVertex(pose, p[0] / 16, p[1] / 16, p[2] / 16).setColor(-1)
                .setUv(p[3], p[4]).setOverlay(overlay).setLight(light)
                .setNormal(pose, p[5], p[6], p[7]);
    }
}
