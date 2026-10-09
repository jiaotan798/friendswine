package com.friendswine.mixin;
import com.friendswine.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Villager.class)
abstract class VillagerMixin implements NitwitData {
    @Unique private CompoundTag friendswine$data = new CompoundTag();
    public CompoundTag friendswine$data() { return friendswine$data; }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL")) private void save(CompoundTag tag,CallbackInfo ci) { tag.put("FriendsWine",friendswine$data.copy()); }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL")) private void load(CompoundTag tag,CallbackInfo ci) { friendswine$data=tag.getCompound("FriendsWine").copy(); }
    @Inject(method="mobInteract",at=@At("HEAD")) private void trade(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci) { NitwitTrades.interact((Villager)(Object)this,player,hand); }
    @Inject(method="tick",at=@At("TAIL")) private void tick(CallbackInfo ci) { NitwitTrades.tick((Villager)(Object)this); }
}
