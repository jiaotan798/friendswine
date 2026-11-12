package com.friendswine.client;

import com.friendswine.EntityAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** 在保留实体外层动画（包括 YSM）的同时，将拴绳牵引者端换算回模型空间。 */
public final class LeashAnimation {
    private LeashAnimation() {}

    /** 牵引者也可能在跳舞；它的挂点只随自身动画变化，不能继承被拴生物的动画。 */
    public static Vec3 holderPosition(Entity holder, float tick, Vec3 position) {
        if (!(holder instanceof LivingEntity living)) return position;
        EntityAnimation animation = EntityAnimation.forEntity(living, tick);
        if (animation == null) return position;
        Vec3 origin = holder.getPosition(tick);
        Vec3 relative = position.subtract(origin);
        PoseStack pose = new PoseStack();
        DollRenderer.applyAnimation(pose, animation);
        Vector3f transformed = pose.last().pose().transformPosition(
                new Vector3f((float) relative.x, (float) relative.y, (float) relative.z));
        return origin.add(transformed.x, transformed.y, transformed.z);
    }

    /**
     * 原版仍负责绳长、悬垂和生物挂点；只补偿牵引者端，避免整条绳随实体绕转。
     * origin 必须是插值后的实体原点，不能使用当前 tick 位置，否则移动时会抖动。
     */
    public static Vec3 compensate(EntityAnimation animation, Vec3 origin, Vec3 holder) {
        if (animation == null) return holder;
        PoseStack pose = new PoseStack();
        DollRenderer.applyAnimation(pose, animation);
        Vector3f local = inverseHolder(pose.last().pose(), holder.x - origin.x,
                holder.y - origin.y, holder.z - origin.z);
        return origin.add(local.x, local.y, local.z);
    }

    /** A⁻¹h 经外层 A 再变换后仍为真实持绳位置 h；生物挂点则继续接受 A。 */
    public static Vector3f inverseHolder(Matrix4fc animation, double x, double y, double z) {
        // 动画配置限制高度缩放始终大于零，因此这里的旋转缩放矩阵可逆。
        return new Matrix4f(animation).invert().transformPosition(new Vector3f((float) x, (float) y, (float) z));
    }
}
