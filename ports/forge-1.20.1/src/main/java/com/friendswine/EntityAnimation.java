package com.friendswine;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** One frame's animation source, shared by the world model and all three camera views. */
public record EntityAnimation(double seconds, double rotationSeconds, boolean rotating, boolean musical) {
    public float squash() {
        return musical ? JellyAnimation.musicSquash(seconds) : JellyAnimation.squash(seconds);
    }

    @Nullable
    public static EntityAnimation forCamera(Player player, float partialTick) {
        return player.hasEffect(FriendsWine.tipsy()) ? forEntity(player, partialTick) : null;
    }

    @Nullable
    public static EntityAnimation forEntity(LivingEntity entity, float partialTick) {
        if (entity instanceof PartyGuestEntity || !(entity instanceof Mob || entity instanceof Player) || !entity.isAlive()) return null;
        DollBlockEntity doll = DollBlockEntity.nearestActive(entity.level(), entity.position(), 10);
        if (doll != null) {
            long time = entity.level().getGameTime();
            return new EntityAnimation((doll.getElapsedTicks(time) + partialTick) / 20.0,
                    (doll.getRotationElapsedTicks(time) + partialTick) / 20.0,
                    doll.isRotating(), true);
        }
        var personal = entity instanceof Player ? entity.getEffect(FriendsWine.tipsy()) : null;
        if (personal == null) return null;
        // ponytail: remaining ticks encode this fixed-duration drink; configurable/infinite buffs need a synced start clock.
        double seconds = Math.max(0, WineItem.DURATION_TICKS - personal.getDuration() + partialTick) / 20.0;
        return new EntityAnimation(seconds, seconds, false, false);
    }
}
