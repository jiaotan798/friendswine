package com.friendswine.client;

import com.friendswine.GuestDeathNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** 客户端接收器单独注册，专用服务器不会初始化绘制和音效类。 */
final class GuestDeathClientNetworking {
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GuestDeathNetworking.ID, (client, handler, buffer, sender) -> {
            var event = buffer.readUUID();
            String dimension = buffer.readUtf(256);
            client.execute(() -> ClientDeathFlash.start(event, dimension));
        });
    }
}
