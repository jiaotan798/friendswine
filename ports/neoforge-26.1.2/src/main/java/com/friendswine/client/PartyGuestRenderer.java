package com.friendswine.client;

import com.friendswine.ClientConfig;
import com.friendswine.DollBlockEntity;
import com.friendswine.FriendsWine;
import com.friendswine.GuestAnimation;
import com.friendswine.PartyGuestEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/** A feet-anchored, two-sided image plane facing this client's camera, remaining upright. */
public final class PartyGuestRenderer extends EntityRenderer<PartyGuestEntity, PartyGuestRenderer.State> {
    private static final Identifier[] IDLE = {texture("kasumi", "idle"), texture("emma", "idle")};
    private static final Identifier[] DANCE = {texture("kasumi", "dance"), texture("emma", "dance")};

    public PartyGuestRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.3F; }
    private static Identifier texture(String name, String state) {
        return Identifier.fromNamespaceAndPath("friendswine", "textures/entity/" + name + "/" + state + ".png");
    }

    public static final class State extends EntityRenderState {
        public Identifier texture;
        public float scale, halfWidth, u0, v0, u1, v1;
    }
    @Override public State createRenderState() { return new State(); }

    @Override public boolean shouldRender(PartyGuestEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        double size = Math.max(1, ClientConfig.creatureMax(entity.getType() == FriendsWine.EMMA.get()));
        AABB bounds = new AABB(entity.getX() - size / 2, entity.getY(), entity.getZ() - size / 2,
                entity.getX() + size / 2, entity.getY() + size, entity.getZ() + size / 2);
        return entity.distanceToSqr(camX, camY, camZ) <= (128 + size) * (128 + size) && frustum.isVisible(bounds.inflate(0.5));
    }

    @Override public void extractRenderState(PartyGuestEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        boolean emma = entity.getType() == FriendsWine.EMMA.get();
        DollBlockEntity doll = DollBlockEntity.nearestActive(entity.level(), entity.position(), 20);
        boolean dancing = doll != null;
        double seconds = dancing ? (doll.getElapsedTicks(entity.level().getGameTime()) + partialTick) / 20.0 : 0;
        int frame = dancing ? GuestAnimation.frame(emma, seconds) : 0;
        int columns = dancing ? GuestAnimation.columns(emma) : 1;
        int rows = dancing ? GuestAnimation.rows(emma) : 1;
        state.texture = dancing ? DANCE[emma ? 1 : 0] : IDLE[emma ? 1 : 0];
        state.scale = dancing ? GuestAnimation.scale(seconds, ClientConfig.creatureMin(emma), ClientConfig.creatureMax(emma)) : 1;
        state.halfWidth = GuestAnimation.aspect(emma, dancing) / 2;
        state.u0 = (float) (frame % columns) / columns; state.v0 = (float) (frame / columns) / rows;
        state.u1 = state.u0 + 1F / columns; state.v1 = state.v0 + 1F / rows;
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.isInvisible) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(GuestAnimation.facingYaw(camera.pos.x - state.x, camera.pos.z - state.z, camera.yRot)));
            pose.scale(state.scale, state.scale, state.scale);
            // Capture primitive values: the deferred collector must not retain mutable render state.
            float width = state.halfWidth, u0 = state.u0, v0 = state.v0, u1 = state.u1, v1 = state.v1;
            int light = state.lightCoords;
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(state.texture), (transform, consumer) -> {
                vertex(consumer, transform, -width, 0, u0, v1, light);
                vertex(consumer, transform, width, 0, u1, v1, light);
                vertex(consumer, transform, width, 1, u1, v0, light);
                vertex(consumer, transform, -width, 1, u0, v0, light);
            });
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        out.addVertex(pose, x, y, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0, 0, 1);
    }
}
