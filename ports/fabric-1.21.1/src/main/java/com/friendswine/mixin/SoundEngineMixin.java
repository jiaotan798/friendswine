package com.friendswine.mixin;
import com.friendswine.client.DollMusicSound;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.sounds.*;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
    @WrapOperation(method="play",at=@At(value="INVOKE",target="Lnet/minecraft/client/sounds/SoundBufferLibrary;getStream(Lnet/minecraft/resources/ResourceLocation;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<AudioStream> stream(SoundBufferLibrary buffers,ResourceLocation path,boolean looping,Operation<CompletableFuture<AudioStream>> original,SoundInstance instance) {
        return instance instanceof DollMusicSound song ? song.getStream(buffers,instance.getSound(),looping) : original.call(buffers,path,looping);
    }
}
