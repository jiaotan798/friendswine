package com.friendswine.mixin;
import com.friendswine.DollPeace;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Mob.class)
abstract class MobMixin {
    @Inject(method="setTarget",at=@At("HEAD"),cancellable=true) private void target(LivingEntity target,CallbackInfo ci) {
        Mob mob=(Mob)(Object)this;
        if (!(mob instanceof Creeper) && target != null && DollPeace.protectedCombat(mob,target)) ci.cancel();
    }
    @Inject(method="tick",at=@At("HEAD")) private void tick(CallbackInfo ci) { DollPeace.tick((Mob)(Object)this); }
}
