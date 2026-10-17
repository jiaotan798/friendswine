package com.friendswine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraftforge.network.*;
public final class SettingsNetworking {
    // 网络边界严格检查枚举，不能把恶意／过期客户端提交的无效值默认为合法修改。
    private static boolean validScope(String scope) { return "NEARBY".equals(scope) || "GLOBAL".equals(scope); }
    public static DeathFlashScope clientScope=DeathFlashScope.NEARBY;
    public static double clientOrbit=1; public static boolean canEdit;
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("friendswine","settings"),()->"2","2"::equals,"2"::equals);
    record State(double speed,boolean editable,String scope){}
    record Change(double speed,String scope){}
    private static boolean allowed(ServerPlayer p){return p.hasPermissions(2)||p.server.isSingleplayerOwner(p.getGameProfile());}
    private static void send(ServerPlayer p){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(ServerConfig.ORBIT_SPEED.get().doubleValue(),allowed(p),ServerConfig.deathFlashScope().name()));}
    private static void broadcast(net.minecraft.server.MinecraftServer s){s.getPlayerList().getPlayers().forEach(SettingsNetworking::send);}
    public static void change(double speed,DeathFlashScope scope){CHANNEL.sendToServer(new Change(speed,scope.name()));}
    public static void register(){
        CHANNEL.registerMessage(0,State.class,(p,b)->{b.writeDouble(p.speed);b.writeBoolean(p.editable);b.writeUtf(p.scope,16);},b->new State(b.readDouble(),b.readBoolean(),b.readUtf(16)),(p,c)->{var ctx=c.get();ctx.enqueueWork(()->{clientOrbit=p.speed;canEdit=p.editable;clientScope=DeathFlashScope.fromConfig(p.scope);});ctx.setPacketHandled(true);},java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Change.class,(p,b)->{b.writeDouble(p.speed);b.writeUtf(p.scope,16);},b->new Change(b.readDouble(),b.readUtf(16)),(p,c)->{var ctx=c.get();ctx.enqueueWork(()->{var player=ctx.getSender();if(player==null)return;if(!allowed(player)||!Double.isFinite(p.speed)||p.speed<0||p.speed>4||!validScope(p.scope)){send(player);return;}try{ServerConfig.save(p.speed,DeathFlashScope.valueOf(p.scope));broadcast(player.server);}catch(RuntimeException error){player.sendSystemMessage(Component.literal("无法保存服务器设置"));}});ctx.setPacketHandled(true);},java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p)send(p);});
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e)->e.getDispatcher().register(Commands.literal("friendswine").then(Commands.literal("orbitSpeed").requires(s->s.hasPermission(2)).then(Commands.argument("multiplier",DoubleArgumentType.doubleArg(0,4)).executes(c->{double speed=DoubleArgumentType.getDouble(c,"multiplier");if(!Double.isFinite(speed))return 0;try{ServerConfig.save(speed);broadcast(c.getSource().getServer());}catch(RuntimeException error){c.getSource().sendFailure(Component.literal("无法保存服务器设置"));return 0;}c.getSource().sendSuccess(()->Component.literal("orbitSpeed="+speed),true);return 1;})))));
    }
}
