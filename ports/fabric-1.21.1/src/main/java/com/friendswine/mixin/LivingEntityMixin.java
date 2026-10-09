package com.friendswine.mixin;
import com.friendswine.DollPeace;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true) private void hurt(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci) {
        if (DollPeace.cancelDamage(source,(LivingEntity)(Object)this)) ci.setReturnValue(false);
    }
}
