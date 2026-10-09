package com.friendswine.mixin;

import com.friendswine.client.FriendsWineClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0, shift = At.Shift.AFTER))
    private void friendswine$begin(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                    PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        // YSM replaces the renderer invocation inside this bracket, so it inherits the same transform.
        pose.pushPose();
        if (entity instanceof LivingEntity living) FriendsWineClient.animateEntity(living, partialTick, pose);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z"))
    private void friendswine$end(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                  PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        // Restore before flames, shadows and debug hitboxes; never affect another entity's matrix.
        pose.popPose();
    }
}
