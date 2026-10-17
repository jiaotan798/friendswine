package com.friendswine.client;

import com.friendswine.GuestDeathNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** 客户端接收器单独注册，专用服务器不会初始化绘制和音效类。 */
final class GuestDeathClientNetworking {
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GuestDeathNetworking.Event.TYPE, (packet, context) ->
                context.client().execute(() -> ClientDeathFlash.start(packet.event(), packet.dimension())));
    }
}
