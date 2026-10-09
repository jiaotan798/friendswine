package com.friendswine.mixin;
import com.friendswine.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.villager.Villager;
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
    @Inject(method="addAdditionalSaveData",at=@At("TAIL")) private void save(net.minecraft.world.level.storage.ValueOutput tag,CallbackInfo ci) { tag.store("FriendsWine",CompoundTag.CODEC,friendswine$data.copy()); }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL")) private void load(net.minecraft.world.level.storage.ValueInput tag,CallbackInfo ci) { friendswine$data=tag.read("FriendsWine",CompoundTag.CODEC).orElseGet(CompoundTag::new).copy(); }
    @Inject(method="mobInteract",at=@At("HEAD")) private void trade(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci) { NitwitTrades.interact((Villager)(Object)this,player,hand); }
    @Inject(method="tick",at=@At("TAIL")) private void tick(CallbackInfo ci) { NitwitTrades.tick((Villager)(Object)this); }
}
