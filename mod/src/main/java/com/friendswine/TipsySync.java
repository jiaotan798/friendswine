package com.friendswine;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Vanilla only sends player effects to their owner/passengers; world renderers need them on observers too. */
final class TipsySync {
    private static final Map<ServerPlayer, Integer> REMAINING = new WeakHashMap<>();

    private TipsySync() {}

    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
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

    static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            var effect = target.getEffect(FriendsWine.TIPSY);
            if (effect != null) {
                observer.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), effect, true));
            }
        }
    }
}
