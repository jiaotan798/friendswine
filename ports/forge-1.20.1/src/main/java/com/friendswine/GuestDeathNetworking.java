package com.friendswine;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** 死亡事件只由服务端产生；公屏播报独立于闪屏范围，不接受客户端触发请求。 */
public final class GuestDeathNetworking {

    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL = net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
            new ResourceLocation("friendswine", "guest_death_flash"), () -> "1", "1"::equals, "1"::equals);
    public record Event(UUID event, String dimension) {}
    public static void register() {
        CHANNEL.registerMessage(0, Event.class,
                (event, buffer) -> { buffer.writeUUID(event.event()); buffer.writeUtf(event.dimension(), 256); },
                buffer -> new Event(buffer.readUUID(), buffer.readUtf(256)),
                (event, context) -> {
                    var ctx = context.get();
                    ctx.enqueueWork(() -> com.friendswine.client.ClientDeathFlash.start(event.event(), event.dimension()));
                    ctx.setPacketHandled(true);
                }, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }
    private static void send(ServerPlayer player, Event event) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), event);
    }

    /** 接收者在死亡时确定；设置变化和玩家随后移动不会改变已发送事件。 */
    static void trigger(PartyGuestEntity guest, ServerPlayer killer) {
        var server = guest.level().getServer();
        server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.friendswine.guestKilled", guest.getDisplayName(), killer.getDisplayName()), false);
        Event event = new Event(UUID.randomUUID(), guest.level().dimension().location().toString());
        for (var player : recipients(guest, ServerConfig.deathFlashScope())) send(player, event);
    }
    /** 仅按服务端范围筛选在线玩家，包含创造和旁观者，不要求视线。 */
    static java.util.List<ServerPlayer> recipients(PartyGuestEntity guest, DeathFlashScope scope) {
        return guest.level().getServer().getPlayerList().getPlayers().stream().filter(player ->
                scope.includes(player.level().dimension().equals(guest.level().dimension()), player.distanceToSqr(guest))).toList();
    }
}
