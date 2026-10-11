package com.friendswine.client;

import com.friendswine.ClientConfig;
import com.friendswine.DollBlockEntity;
import com.friendswine.FriendsWine;
import com.friendswine.GuestAnimation;
import com.friendswine.GuestVariant;
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

/** 角色立板朝向本机摄像机；携带玩偶复用同一朝向、缩放和现有立体模型。 */
public final class PartyGuestRenderer extends EntityRenderer<PartyGuestEntity, PartyGuestRenderer.State> {
    private static final Identifier[] IDLE = {texture("kasumi", "idle"), texture("emma", "idle"), texture("amiya", "idle")};
    private static final Identifier[] DANCE = {texture("kasumi", "dance"), texture("emma", "dance"), texture("amiya", "dance")};

    public PartyGuestRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.3F; }
    private static Identifier texture(String name, String state) {
        return Identifier.fromNamespaceAndPath("friendswine", "textures/entity/" + name + "/" + state + ".png");
    }

    public static final class State extends EntityRenderState {
        public Identifier texture;
        public float scale, halfWidth, u0, v0, u1, v1;
        public GuestVariant variant;
        public boolean carryingDoll;
    }
    @Override public State createRenderState() { return new State(); }

    @Override public boolean shouldRender(PartyGuestEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        double size = Math.max(1, ClientConfig.creatureMax(entity.variant()));
        AABB bounds = new AABB(entity.getX() - size / 2, entity.getY(), entity.getZ() - size / 2,
                entity.getX() + size / 2, entity.getY() + size, entity.getZ() + size / 2);
        return entity.distanceToSqr(camX, camY, camZ) <= (128 + size) * (128 + size) && frustum.isVisible(bounds.inflate(0.5));
    }

    @Override public void extractRenderState(PartyGuestEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        GuestVariant variant = entity.variant();
        state.variant = variant;
        // 每帧完整赋值，空手和放置成功后不能复用上一帧的携带状态。
        state.carryingDoll = entity.isCarryingDoll();
        DollBlockEntity doll = DollBlockEntity.nearestActive(entity.level(), entity.position(), 20);
        boolean dancing = doll != null;
        double seconds = dancing ? (doll.getElapsedTicks(entity.level().getGameTime()) + partialTick) / 20.0 : 0;
        int frame = dancing ? GuestAnimation.frame(variant, seconds) : 0;
        int columns = dancing ? GuestAnimation.columns(variant) : 1;
        int rows = dancing ? GuestAnimation.rows(variant) : 1;
        state.texture = dancing ? DANCE[variant.ordinal()] : IDLE[variant.ordinal()];
        state.scale = dancing ? GuestAnimation.scale(seconds, ClientConfig.creatureMin(variant), ClientConfig.creatureMax(variant)) : 1;
        state.halfWidth = GuestAnimation.aspect(variant, dancing) / 2;
        state.u0 = (float) (frame % columns) / columns; state.v0 = (float) (frame / columns) / rows;
        state.u1 = state.u0 + 1F / columns; state.v1 = state.v0 + 1F / rows;
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.isInvisible) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(GuestAnimation.facingYaw(camera.pos.x - state.x, camera.pos.z - state.z, camera.yRot)));
            pose.scale(state.scale, state.scale, state.scale);
            // 延迟提交需复制数值，不能闭包引用下一帧会被更新的渲染状态。
            float width = state.halfWidth, u0 = state.u0, v0 = state.v0, u1 = state.u1, v1 = state.v1;
            int light = state.lightCoords;
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(state.texture), (transform, consumer) -> {
                vertex(consumer, transform, -width, 0, u0, v1, light);
                vertex(consumer, transform, width, 0, u1, v1, light);
                vertex(consumer, transform, width, 1, u1, v0, light);
                vertex(consumer, transform, -width, 1, u0, v0, light);
            });
            if (state.carryingDoll) {
                // shortcut: 使用固定手边挂点，不追踪 GIF 手部；需要精确握持时改为逐帧挂点。
                pose.pushPose();
                pose.translate(state.variant.handX, state.variant.handY, 0.15);
                pose.scale(0.3F, 0.3F, 0.3F);
                DollRenderer.draw(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        out.addVertex(pose, x, y, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0, 0, 1);
    }
}
