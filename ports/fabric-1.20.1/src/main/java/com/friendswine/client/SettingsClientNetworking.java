package com.friendswine.client;
import com.friendswine.SettingsNetworking;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
final class SettingsClientNetworking {
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SettingsNetworking.STATE,(client,handler,buf,sender)->{
            double speed=buf.readDouble(); boolean editable=buf.readBoolean();
            client.execute(()->{ SettingsNetworking.clientOrbit=speed; SettingsNetworking.canEdit=editable; });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{ SettingsNetworking.clientOrbit=1; SettingsNetworking.canEdit=false; });
    }
    static void change(double speed) { var buf=PacketByteBufs.create(); buf.writeDouble(speed); ClientPlayNetworking.send(SettingsNetworking.CHANGE,buf); }
}
