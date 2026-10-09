package com.friendswine.mixin;

import com.friendswine.EntityAnimation;
import com.friendswine.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", ordinal = 0, shift = At.Shift.AFTER))
    private void friendswine$effectCamera(BlockGetter level, Entity entity, boolean detached,
                                       boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof Player player) || player != Minecraft.getInstance().player
                || !player.isAlive() || player.isSleeping()) return;
        EntityAnimation animation = EntityAnimation.forCamera(player, partialTick);
        if (animation == null) return;
        Vec3 position = ((Camera) (Object) this).getPosition();
        double feetY = Mth.lerp((double) partialTick, player.yo, player.getY());
        // Adjust the fresh eye-height anchor before third-person reversal, zoom collision and movement.
        double offset = (position.y - feetY) * (ClientConfig.heightScale(animation.squash()) - 1);
        setPosition(position.add(0, offset, 0));
    }
}
