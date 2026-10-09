package com.friendswine.client;

import com.friendswine.DollBlockEntity;
import com.friendswine.EntityAnimation;
import com.friendswine.FriendsWine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Mod(value = FriendsWine.MODID, dist = Dist.CLIENT)
public final class FriendsWineClient {
    private final Map<BlockPos, DollMusicSound> sounds = new HashMap<>();
    private Object lastLevel;
    private int soundCheckTicks;

    public FriendsWineClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(this::renderers);
        modBus.addListener(this::itemRenderers);
        modBus.addListener(this::reloadListeners);
        NeoForge.EVENT_BUS.addListener(this::clientTick);
    }

    private void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FriendsWine.DOLL_BLOCK_ENTITY.get(), DollRenderer::new);
        event.registerEntityRenderer(FriendsWine.KASUMI.get(), PartyGuestRenderer::new);
        event.registerEntityRenderer(FriendsWine.EMMA.get(), PartyGuestRenderer::new);
    }

    private void itemRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("friendswine","doll"),DollItemRenderer.Unbaked.CODEC);
    }

    private void reloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("friendswine","doll_mesh"),(ResourceManagerReloadListener) manager -> DollRenderer.invalidateMesh());
    }

    /** Client check: start muted, unmute; then mute/unmute RECORDS and press F3+T during playback. */
    private void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
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
