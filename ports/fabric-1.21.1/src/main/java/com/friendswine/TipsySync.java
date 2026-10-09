package com.friendswine;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;

/** Vanilla only sends player effects to their owner/passengers; world renderers need them on observers too. */
public final class TipsySync {
    private static final Map<ServerPlayer, Integer> REMAINING = new WeakHashMap<>();

    private TipsySync() {}

    public static void tick(ServerPlayer player) {
        var effect = player.getEffect(FriendsWine.TIPSY);
        if (effect == null) {
            if (REMAINING.remove(player) != null) {
                player.serverLevel().getChunkSource().broadcast(player,
                        new ClientboundRemoveMobEffectPacket(player.getId(), FriendsWine.TIPSY));
            }
            return;
        }
        int remaining = effect.getDuration();
        Integer previous = REMAINING.put(player, remaining);
        // Send starts/refreshes, not a packet for each ordinary countdown tick. Poll after cures/cancellation settle.
        if (previous == null || remaining == -1 && previous != -1
                || remaining >= 0 && (previous < 0 || remaining >= previous)) {
            player.serverLevel().getChunkSource().broadcast(player,
                    new ClientboundUpdateMobEffectPacket(player.getId(), effect, true));
        }
    }

    public static void startTracking(ServerPlayer observer, net.minecraft.world.entity.Entity entity) {
        if (entity instanceof ServerPlayer target) {
            var effect = target.getEffect(FriendsWine.TIPSY);
            if (effect != null) {
                observer.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), effect, true));
            }
        }
    }
}
