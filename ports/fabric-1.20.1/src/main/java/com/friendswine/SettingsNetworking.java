package com.friendswine;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SettingsNetworking {
    public static double clientOrbit=1;
    public static boolean canEdit;
    public static final ResourceLocation STATE=new ResourceLocation("friendswine","settings"), CHANGE=new ResourceLocation("friendswine","orbit_speed");
    private static boolean allowed(ServerPlayer player) { return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile()); }
    private static void send(ServerPlayer player) { var buf=PacketByteBufs.create(); buf.writeDouble(ServerConfig.ORBIT_SPEED.get()); buf.writeBoolean(allowed(player)); ServerPlayNetworking.send(player,STATE,buf); }
    private static void broadcast(MinecraftServer server) { server.getPlayerList().getPlayers().forEach(SettingsNetworking::send); }
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->send(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(CHANGE,(server,player,handler,buf,sender)->{
            double speed=buf.readDouble();
            server.execute(()->{
                if (!allowed(player) || !Double.isFinite(speed) || speed<0 || speed>4) { send(player); return; }
                try { ServerConfig.save(speed); broadcast(server); }
                catch (RuntimeException error) { player.sendSystemMessage(Component.literal("朋友的酒：无法保存服务器设置")); }
            });
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(Commands.literal("friendswine")
                .then(Commands.literal("orbitSpeed").requires(source->source.hasPermission(2))
                        .executes(context->{ context.getSource().sendSuccess(()->Component.literal("orbitSpeed="+ServerConfig.ORBIT_SPEED.get()),false); return 1; })
                        .then(Commands.argument("multiplier",DoubleArgumentType.doubleArg(0,4)).executes(context->{
                            double speed=DoubleArgumentType.getDouble(context,"multiplier");
                            if (!Double.isFinite(speed)) return 0;
                            try { ServerConfig.save(speed); broadcast(context.getSource().getServer()); }
                            catch (RuntimeException error) { context.getSource().sendFailure(Component.literal("无法保存服务器设置")); return 0; }
                            context.getSource().sendSuccess(()->Component.literal("orbitSpeed="+speed),true); return 1;
                        })))));
    }
}
