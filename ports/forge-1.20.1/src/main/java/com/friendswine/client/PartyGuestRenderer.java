package com.friendswine.client;

import com.friendswine.ClientConfig;
import com.friendswine.DollBlockEntity;
import com.friendswine.FriendsWine;
import com.friendswine.GuestAnimation;
import com.friendswine.PartyGuestEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** A feet-anchored, two-sided image plane facing this client's camera, remaining upright. */
public final class PartyGuestRenderer extends EntityRenderer<PartyGuestEntity> {
    private static final ResourceLocation[] IDLE = {texture("kasumi", "idle"), texture("emma", "idle")};
    private static final ResourceLocation[] DANCE = {texture("kasumi", "dance"), texture("emma", "dance")};

    public PartyGuestRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.3F; }
    private static ResourceLocation texture(String name, String state) {
        return new ResourceLocation("friendswine", "textures/entity/" + name + "/" + state + ".png");
    }

    @Override public ResourceLocation getTextureLocation(PartyGuestEntity entity) {
        boolean emma = entity.getType() == FriendsWine.EMMA.get();
        return DollBlockEntity.nearestActive(entity.level(), entity.position(), 20) == null ? IDLE[emma ? 1 : 0] : DANCE[emma ? 1 : 0];
    }

    @Override public boolean shouldRender(PartyGuestEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        double size = Math.max(1, ClientConfig.creatureMax(entity.getType() == FriendsWine.EMMA.get()));
        AABB bounds = new AABB(entity.getX() - size / 2, entity.getY(), entity.getZ() - size / 2,
                entity.getX() + size / 2, entity.getY() + size, entity.getZ() + size / 2);
        return entity.distanceToSqr(camX, camY, camZ) <= (128 + size) * (128 + size) && frustum.isVisible(bounds.inflate(0.5));
    }

    @Override public void render(PartyGuestEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!entity.isInvisible()) {
            boolean emma = entity.getType() == FriendsWine.EMMA.get();
            DollBlockEntity doll = DollBlockEntity.nearestActive(entity.level(), entity.position(), 20);
            boolean dancing = doll != null;
            double seconds = dancing ? (doll.getElapsedTicks(entity.level().getGameTime()) + partialTick) / 20.0 : 0;
            int frame = dancing ? GuestAnimation.frame(emma, seconds) : 0;
            int columns = dancing ? GuestAnimation.columns(emma) : 1;
            int rows = dancing ? GuestAnimation.rows(emma) : 1;
            float u0 = (float) (frame % columns) / columns, v0 = (float) (frame / columns) / rows;
            float u1 = u0 + 1F / columns, v1 = v0 + 1F / rows;
            float scale = dancing ? GuestAnimation.scale(seconds, ClientConfig.creatureMin(emma), ClientConfig.creatureMax(emma)) : 1;
            float halfWidth = GuestAnimation.aspect(emma, dancing) / 2;
            pose.pushPose();
            var camera = entityRenderDispatcher.camera;
            var position = entity.getPosition(partialTick);
            var eye = camera.getPosition();
            pose.mulPose(Axis.YP.rotationDegrees(GuestAnimation.facingYaw(eye.x - position.x, eye.z - position.z, camera.getYRot())));
            pose.scale(scale, scale, scale);
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(dancing ? DANCE[emma ? 1 : 0] : IDLE[emma ? 1 : 0]));
            vertex(consumer, pose.last(), -halfWidth, 0, u0, v1, light);
            vertex(consumer, pose.last(), halfWidth, 0, u1, v1, light);
            vertex(consumer, pose.last(), halfWidth, 1, u1, v0, light);
            vertex(consumer, pose.last(), -halfWidth, 1, u0, v0, light);
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        out.vertex(pose.pose(), x, y, 0).color(255,255,255,255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.normal(), 0, 0, 1).endVertex();
    }
}
