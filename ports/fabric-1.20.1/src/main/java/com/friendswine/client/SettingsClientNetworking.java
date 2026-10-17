package com.friendswine.client;
import com.friendswine.SettingsNetworking;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
final class SettingsClientNetworking {
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SettingsNetworking.STATE,(client,handler,buf,sender)->{
            double speed=buf.readDouble(); boolean editable=buf.readBoolean(); String scope=buf.readUtf(16);
            client.execute(()->{ SettingsNetworking.clientOrbit=speed; SettingsNetworking.canEdit=editable; SettingsNetworking.clientScope=com.friendswine.DeathFlashScope.fromConfig(scope); });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{ SettingsNetworking.clientScope=com.friendswine.DeathFlashScope.NEARBY; SettingsNetworking.clientOrbit=1; SettingsNetworking.canEdit=false; });
    }
    static void change(double speed,com.friendswine.DeathFlashScope scope) { var buf=PacketByteBufs.create(); buf.writeDouble(speed); buf.writeUtf(scope.name(),16); ClientPlayNetworking.send(SettingsNetworking.CHANGE,buf); }
}
