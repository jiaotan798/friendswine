package com.friendswine;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SettingsNetworking {
    public static double clientOrbit=1;
    public static boolean canEdit;
    public record State(double speed,boolean editable) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(Identifier.fromNamespaceAndPath("friendswine","settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.composite(ByteBufCodecs.DOUBLE,State::speed,ByteBufCodecs.BOOL,State::editable,State::new);
        public Type<State> type() { return TYPE; }
    }
    public record Change(double speed) implements CustomPacketPayload {
        public static final Type<Change> TYPE=new Type<>(Identifier.fromNamespaceAndPath("friendswine","orbit_speed"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Change> CODEC=StreamCodec.composite(ByteBufCodecs.DOUBLE,Change::speed,Change::new);
        public Type<Change> type() { return TYPE; }
    }
    private static boolean allowed(ServerPlayer player) { return player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER) || player.level().getServer().isSingleplayerOwner(player.nameAndId()); }
    private static void send(ServerPlayer player) { ServerPlayNetworking.send(player,new State(ServerConfig.ORBIT_SPEED.get(),allowed(player))); }
    private static void broadcast(MinecraftServer server) { server.getPlayerList().getPlayers().forEach(SettingsNetworking::send); }
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE,State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Change.TYPE,Change.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->send(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(Change.TYPE,(packet,context)->context.server().execute(()->{
            if (!allowed(context.player()) || !Double.isFinite(packet.speed()) || packet.speed()<0 || packet.speed()>4) { send(context.player()); return; }
            try { ServerConfig.save(packet.speed()); broadcast(context.server()); }
            catch (RuntimeException error) { context.player().sendSystemMessage(Component.literal("朋友的酒：无法保存服务器设置")); }
        }));
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(Commands.literal("friendswine")
                .then(Commands.literal("orbitSpeed").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
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
