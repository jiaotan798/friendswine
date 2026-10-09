package com.friendswine.client;

import com.friendswine.DollBlockEntity;
import com.friendswine.EntityAnimation;
import com.friendswine.FriendsWine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class FriendsWineClient {
    private final Map<BlockPos, DollMusicSound> sounds = new HashMap<>();
    private Object lastLevel;
    private int soundCheckTicks;

    public static boolean openSettings;
    public static void register() {
        var instance=new FriendsWineClient();
        var bus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener((net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e)-> {
            e.registerBlockEntityRenderer(FriendsWine.DOLL_BLOCK_ENTITY.get(),DollRenderer::new);
            e.registerEntityRenderer(FriendsWine.KASUMI.get(),PartyGuestRenderer::new);
            e.registerEntityRenderer(FriendsWine.EMMA.get(),PartyGuestRenderer::new);
        });
        bus.addListener((net.minecraftforge.client.event.RegisterClientReloadListenersEvent e)->e.registerReloadListener((ResourceManagerReloadListener)manager->DollRenderer.invalidateMesh()));
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,()->new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((mc,parent)->new SettingsScreen(parent)));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent e)->{if(e.phase==net.minecraftforge.event.TickEvent.Phase.END)instance.clientTick();});
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RegisterClientCommandsEvent e)->e.getDispatcher().register(net.minecraft.commands.Commands.literal("friendswine").then(net.minecraft.commands.Commands.literal("settings").executes(ctx->{openSettings=true;return 1;}))));
    }

    /** Client check: start muted, unmute; then mute/unmute RECORDS and press F3+T during playback. */
    private void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if(openSettings){openSettings=false;mc.setScreen(new SettingsScreen(null));}
        if (lastLevel != mc.level) {
            sounds.values().forEach(DollMusicSound::cancel);
            sounds.clear();
            lastLevel = mc.level;
        }
        if (mc.level == null || mc.isPaused()) return;
        if (mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || mc.options.getSoundSourceVolume(SoundSource.RECORDS) <= 0) {
            sounds.values().forEach(DollMusicSound::cancel);
            sounds.clear();
            return;
        }
        boolean checkSoundChannels = ++soundCheckTicks % 20 == 0;
        var active = DollBlockEntity.getActiveDolls(mc.level);
        var positions = new HashSet<BlockPos>();
        for (DollBlockEntity doll : active) {
            BlockPos pos = doll.getBlockPos();
            if (mc.player == null || mc.player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 400) continue;
            positions.add(pos);
            DollMusicSound sound = sounds.get(pos);
            // isActive includes a reserved channel while its stream is still loading asynchronously.
            if (sound == null || !sound.matchesPlayingDoll(doll)
                    || (checkSoundChannels && !mc.getSoundManager().isActive(sound))) {
                if (sound != null) sound.cancel();
                sound = new DollMusicSound(doll);
                sounds.put(pos, sound);
                mc.getSoundManager().play(sound);
            }
        }
        sounds.entrySet().removeIf(entry -> {
            if (positions.contains(entry.getKey())) return false;
            entry.getValue().cancel();
            return true;
        });
    }

    public static void animateEntity(LivingEntity entity, float partialTick, PoseStack pose) {
        EntityAnimation animation = EntityAnimation.forEntity(entity, partialTick);
        if (animation != null) {
            DollRenderer.applyAnimation(pose, animation);
        }
    }
}
