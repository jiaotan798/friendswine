package com.friendswine.mixin;
import com.friendswine.EntityAnimation;
import com.friendswine.client.DollRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @Unique private static final java.util.Map<EntityRenderState,EntityAnimation> friendswine$animations=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    @Inject(method="extractEntity",at=@At("RETURN")) private void extract(Entity entity,float tick,CallbackInfoReturnable<EntityRenderState> ci) {
        EntityAnimation animation=entity instanceof LivingEntity living?EntityAnimation.forEntity(living,tick):null;
        if(animation==null)friendswine$animations.remove(ci.getReturnValue());else friendswine$animations.put(ci.getReturnValue(),animation);
    }
    @Inject(method="submit",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",ordinal=0,shift=At.Shift.AFTER))
    private void begin(EntityRenderState state,CameraRenderState camera,double x,double y,double z,PoseStack pose,SubmitNodeCollector collector,CallbackInfo ci) {
        pose.pushPose();var animation=friendswine$animations.get(state);if(animation!=null)DollRenderer.applyAnimation(pose,animation);
    }
    @Inject(method="submit",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/entity/state/EntityRenderState;displayFireAnimation:Z",ordinal=0))
    private void end(EntityRenderState state,CameraRenderState camera,double x,double y,double z,PoseStack pose,SubmitNodeCollector collector,CallbackInfo ci){pose.popPose();}
}
