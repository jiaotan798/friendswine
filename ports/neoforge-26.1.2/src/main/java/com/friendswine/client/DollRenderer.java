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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class DollRenderer implements BlockEntityRenderer<DollBlockEntity,DollRenderer.State> {
    private static final Identifier MESH = Identifier.fromNamespaceAndPath("friendswine", "models/entity/doll.json");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("friendswine", "textures/entity/doll.png");
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

    public static final class State extends BlockEntityRenderState {
        float yaw; EntityAnimation animation;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(DollBlockEntity doll,State state,float partialTick,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(doll,state,partialTick,camera,breaking);
        state.yaw=180-doll.getBlockState().getValue(DollBlock.FACING).toYRot();
        state.animation=doll.isPlaying() && doll.getLevel()!=null ? new EntityAnimation((doll.getElapsedTicks(doll.getLevel().getGameTime())+partialTick)/20.0,(doll.getRotationElapsedTicks(doll.getLevel().getGameTime())+partialTick)/20.0,doll.isRotating(),true) : null;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        pose.pushPose(); pose.translate(0.5,0,0.5); pose.mulPose(Axis.YP.rotationDegrees(state.yaw));
        if(state.animation!=null){pose.translate(0,0,1.0/16);applyAnimation(pose,state.animation);pose.translate(0,0,-1.0/16);}
        draw(pose,collector,state.lightCoords,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY); pose.popPose();
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

    public static void draw(PoseStack pose,SubmitNodeCollector collector,int light,int overlay) {
        float[][] points=mesh();
        collector.submitCustomGeometry(pose,RenderTypes.entityCutout(TEXTURE),(transform,consumer)->{
            for(int i=0;i<points.length;i+=3){vertex(consumer,transform,points[i],light,overlay);vertex(consumer,transform,points[i+1],light,overlay);vertex(consumer,transform,points[i+2],light,overlay);vertex(consumer,transform,points[i+2],light,overlay);}
        });
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float[] p, int light, int overlay) {
        out.addVertex(pose, p[0] / 16, p[1] / 16, p[2] / 16).setColor(-1)
                .setUv(p[3], p[4]).setOverlay(overlay).setLight(light)
                .setNormal(pose, p[5], p[6], p[7]);
    }
}
