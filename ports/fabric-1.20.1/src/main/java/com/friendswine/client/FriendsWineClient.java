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

public final class FriendsWineClient implements net.fabricmc.api.ClientModInitializer {
    private final Map<BlockPos, DollMusicSound> sounds = new HashMap<>();
    private Object lastLevel;
    private int soundCheckTicks;

    public static boolean openSettings;
    @Override public void onInitializeClient() {
        com.friendswine.ClientConfig.load();
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(FriendsWine.DOLL_BLOCK_ENTITY.get(), DollRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(FriendsWine.KASUMI.get(), PartyGuestRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(FriendsWine.EMMA.get(), PartyGuestRenderer::new);
        DollItemRenderer itemRenderer = new DollItemRenderer();
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(FriendsWine.DOLL_ITEM.get(), itemRenderer::renderByItem);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(
                new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    public net.minecraft.resources.ResourceLocation getFabricId() { return new net.minecraft.resources.ResourceLocation("friendswine", "mesh"); }
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) { DollRenderer.invalidateMesh(); }
                });
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) ->
                dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("friendswine")
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("settings").executes(context -> { openSettings = true; return 1; }))));
        SettingsClientNetworking.register();
    }

    /** Client check: start muted, unmute; then mute/unmute RECORDS and press F3+T during playback. */
    private void clientTick(Minecraft mc) {
        if (openSettings) { openSettings = false; mc.setScreen(new SettingsScreen(mc.screen)); }
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
