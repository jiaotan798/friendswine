package com.friendswine;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 死亡事件只由服务端产生；公屏播报独立于闪屏范围，不接受客户端触发请求。 */
public final class GuestDeathNetworking {

    public record Event(UUID event, String dimension) implements CustomPacketPayload {
        public static final Type<Event> TYPE = new Type<>(Identifier.fromNamespaceAndPath("friendswine", "guest_death_flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Event> CODEC = CustomPacketPayload.codec(
                (event, buffer) -> { buffer.writeUUID(event.event()); buffer.writeUtf(event.dimension(), 256); },
                buffer -> new Event(buffer.readUUID(), buffer.readUtf(256)));
        public Type<Event> type() { return TYPE; }
    }

    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Event.TYPE, Event.CODEC, (packet, context) ->
                context.enqueueWork(() -> com.friendswine.client.ClientDeathFlash.start(packet.event(), packet.dimension())));
    }
    private static void send(ServerPlayer player, Event event) { net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, event); }

    /** 接收者在死亡时确定；设置变化和玩家随后移动不会改变已发送事件。 */
    static void trigger(PartyGuestEntity guest, ServerPlayer killer) {
        var server = guest.level().getServer();
        server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.friendswine.guestKilled", guest.getDisplayName(), killer.getDisplayName()), false);
        Event event = new Event(UUID.randomUUID(), guest.level().dimension().identifier().toString());
        for (var player : recipients(guest, ServerConfig.deathFlashScope())) send(player, event);
    }
    /** 仅按服务端范围筛选在线玩家，包含创造和旁观者，不要求视线。 */
    static java.util.List<ServerPlayer> recipients(PartyGuestEntity guest, DeathFlashScope scope) {
        return guest.level().getServer().getPlayerList().getPlayers().stream().filter(player ->
                scope.includes(player.level().dimension().equals(guest.level().dimension()), player.distanceToSqr(guest))).toList();
    }
}
