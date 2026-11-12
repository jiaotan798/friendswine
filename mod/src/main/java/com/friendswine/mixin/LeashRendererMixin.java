package com.friendswine.mixin;

import com.friendswine.EntityAnimation;
import com.friendswine.client.LeashAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 仅校正原版拴绳的持绳端，不改变调度器包住 YSM 的动画范围。 */
@Mixin(EntityRenderer.class)
abstract class LeashRendererMixin {
    /** 在原版计算绳段前换算持绳端，生物挂点仍由原版和外层动画共同处理。 */
    // 原版第一个 Vec3 局部量是持绳位置；后续挂点、光照仍由原始实体数据计算。
    @ModifyVariable(method = "renderLeash", at = @At("STORE"), ordinal = 0)
    private Vec3 friendswine$holdPosition(Vec3 position, Entity entity, float partialTick,
                                         PoseStack pose, MultiBufferSource buffers, Entity leashHolder) {
        position = LeashAnimation.holderPosition(leashHolder, partialTick, position);
        return entity instanceof LivingEntity living
                ? LeashAnimation.compensate(EntityAnimation.forEntity(living, partialTick),
                        entity.getPosition(partialTick), position)
                : position;
    }
}
