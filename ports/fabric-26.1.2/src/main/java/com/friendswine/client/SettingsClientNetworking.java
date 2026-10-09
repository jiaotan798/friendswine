package com.friendswine.client;
import com.friendswine.SettingsNetworking;
import net.fabricmc.fabric.api.client.networking.v1.*;
final class SettingsClientNetworking {
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SettingsNetworking.State.TYPE,(packet,context)->context.client().execute(()->{
            SettingsNetworking.clientOrbit=packet.speed(); SettingsNetworking.canEdit=packet.editable();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{ SettingsNetworking.clientOrbit=1; SettingsNetworking.canEdit=false; });
    }
}
