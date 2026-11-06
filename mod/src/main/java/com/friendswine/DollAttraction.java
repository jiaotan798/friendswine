package com.friendswine;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

final class DollAttraction {
    static final double SEEK_RADIUS = 50;
    private static final double PULL_RADIUS = 10;
    // 聚集与绕圈共用十格站位半径；寻路搜索仍为五十格，视觉形变范围仍为十格。
    private static final double STOP_RADIUS = 10;
    private static final double SPACING = 1.25;
    private static final double GAP = 0.25;
    private static final double ARRIVAL = 0.18;
    private static final double ORBIT_STEP = 2 * Math.PI / 400;
    // Server-thread only. Weak keys do not keep unloaded mobs alive.
    private static final Map<Mob, Control> CONTROLLED = new WeakHashMap<>();

    private DollAttraction() {}

    static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Set<Mob> candidates = new HashSet<>();
        // ponytail: scan active dolls' local entity boxes; spatial indexing is only needed for very large doll counts.
        for (DollBlockEntity doll : DollBlockEntity.getActiveDolls(level)) {
            Vec3 center = Vec3.atCenterOf(doll.getBlockPos());
            candidates.addAll(level.getEntitiesOfClass(Mob.class, new AABB(doll.getBlockPos()).inflate(SEEK_RADIUS),
                    mob -> mob.isAlive() && mob.position().distanceToSqr(center) <= SEEK_RADIUS * SEEK_RADIUS));
        }

        CONTROLLED.entrySet().removeIf(entry -> {
            if (entry.getKey().level() == level && !candidates.contains(entry.getKey())) {
                release(entry.getKey(), entry.getValue());
                return true;
            }
            return false;
        });

        // Stable order makes simultaneous arrivals reproducible without reshuffling existing slots.
        for (Mob mob : candidates.stream().sorted(Comparator.comparingInt(Mob::getId)).toList()) {
            DollBlockEntity doll = DollBlockEntity.nearestActive(level, mob.position(), SEEK_RADIUS);
            if (doll == null) {
                continue;
            }
            BlockPos owner = doll.getBlockPos();
            Control control = CONTROLLED.get(mob);
            if (control == null || !control.owner.equals(owner)) {
                if (control != null) {
                    release(mob, control);
                }
                control = new Control(owner);
                CONTROLLED.put(mob, control);
            }

            if (level.getGameTime() >= control.nextSlotTick) {
                if (control.target == null || control.width != mob.getBbWidth() || control.height != mob.getBbHeight()
                        || !safe(level, mob, control.target)) {
                    control.target = findSlot(level, mob, owner);
                    control.width = mob.getBbWidth();
                    control.height = mob.getBbHeight();
                    control.nextPathTick = 0;
                }
                control.nextSlotTick = level.getGameTime() + 20;
            }

            Vec3 center = Vec3.atCenterOf(owner);
            boolean orbiting = doll.getMode() == DollBlockEntity.ORBIT && control.target != null;
            double orbitSpeed = orbiting ? ServerConfig.ORBIT_SPEED.get().doubleValue() : 0;
            if (orbiting && mob.position().distanceToSqr(control.target) <= 1) {
                double x = control.target.x - center.x, z = control.target.z - center.z;
                double step = ORBIT_STEP * orbitSpeed;
                // +X turns toward -Z: counterclockwise viewed from above with north at the top.
                Vec3 next = new Vec3(center.x + x * Math.cos(step) + z * Math.sin(step),
                        control.target.y, center.z + z * Math.cos(step) - x * Math.sin(step));
                // ponytail: pause an obstructed orbit; dedicated obstacle-aware tracks would need a larger movement system.
                if (safe(level, mob, next)) control.target = next;
            }
            Vec3 target = control.target == null ? new Vec3(center.x, owner.getY(), center.z) : control.target;
            Vec3 toward = target.subtract(mob.position());
            double distance = toward.length();
            if (orbiting && distance <= 1) {
                release(mob, control);
                control.path = null;
                Vec3 motion = mob.getDeltaMovement();
                Vec3 correction = new Vec3(toward.x * 0.35 - motion.x, 0, toward.z * 0.35 - motion.z);
                double limit = 0.1 * Math.max(1, orbitSpeed);
                if (correction.lengthSqr() > limit * limit) correction = correction.normalize().scale(limit);
                mob.setDeltaMovement(motion.add(correction));
                mob.hasImpulse = true;
                continue;
            }
            double arrival = control.target == null ? STOP_RADIUS : orbiting ? 0.01 : ARRIVAL;
            if (distance <= arrival) {
                release(mob, control);
                control.path = null;
                // Remove only horizontal arrival momentum; gravity and vanilla AI remain active.
                Vec3 motion = mob.getDeltaMovement();
                mob.setDeltaMovement(new Vec3(motion.x * 0.45, motion.y, motion.z * 0.45));
                continue;
            }

            if (level.getGameTime() >= control.nextPathTick || mob.getNavigation().getPath() != control.path) {
                mob.getNavigation().moveTo(target.x, target.y, target.z, 1.1);
                control.path = mob.getNavigation().getPath();
                control.nextPathTick = level.getGameTime() + 10;
            }
            if (mob.position().distanceToSqr(center) <= PULL_RADIUS * PULL_RADIUS) {
                Vec3 direction = toward.scale(1.0 / distance);
                double inwardSpeed = mob.getDeltaMovement().dot(direction);
                double acceleration = Math.min(0.035, Math.min((distance - arrival) * 0.06, 0.22 - inwardSpeed));
                if (acceleration > 0) {
                    mob.setDeltaMovement(mob.getDeltaMovement().add(direction.scale(acceleration)));
                    mob.hasImpulse = true;
                }
            }
        }
    }

    /** 在十格圆盘内选择安全、不重叠且优先填充内圈的站位，稳定站位继续复用。 */
    private static Vec3 findSlot(ServerLevel level, Mob mob, BlockPos owner) {
        Vec3 best = null;
        double bestRadius = Double.MAX_VALUE;
        double bestTravel = Double.MAX_VALUE;
        double radius = STOP_RADIUS - mob.getBbWidth() / Math.sqrt(2);
        if (radius <= 0) return null;
        // 空间不足时允许没有站位；仍通过原版碰撞和寻路接近，不传送或穿墙。
        // 六角网格的行距小于列距，按行距计算范围，保证十格圆盘全部可搜索。
        int extent = (int) Math.ceil(STOP_RADIUS / (SPACING * Math.sqrt(3) / 2));
        for (int row = -extent; row <= extent; row++) {
            for (int column = -extent; column <= extent; column++) {
                double x = (column + ((row & 1) == 0 ? 0 : 0.5)) * SPACING;
                double z = row * SPACING * Math.sqrt(3) / 2;
                // 外接圆覆盖绕圈时的所有转角，避免宽体型在转动后切入玩偶。
                double dollClearance = (mob.getBbWidth() + 1) / Math.sqrt(2) + GAP + ARRIVAL;
                if (x * x + z * z > radius * radius || x * x + z * z < dollClearance * dollClearance) continue;
                Vec3 target = null;
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    Vec3 candidate = new Vec3(owner.getX() + 0.5 + x, owner.getY() + dy, owner.getZ() + 0.5 + z);
                    AABB box = boxAt(mob, candidate);
                    if (!box.inflate(GAP).intersects(new AABB(owner)) && safe(level, mob, candidate)) {
                        target = candidate;
                        break;
                    }
                }
                if (target == null) continue;
                double radialDistance = x * x + z * z;
                boolean occupied = false;
                for (var entry : CONTROLLED.entrySet()) {
                    Control other = entry.getValue();
                    if (entry.getKey() == mob || entry.getKey().level() != level || !other.owner.equals(owner)
                            || other.target == null) continue;
                    // 外接圆间距同时保留双方到站误差，绕圈任意转角都不能让碰撞箱互相挤压。
                    double padding = GAP + 2 * ARRIVAL;
                    double spacing = (mob.getBbWidth() + entry.getKey().getBbWidth()) / Math.sqrt(2) + padding;
                    double dx = target.x - other.target.x, dz = target.z - other.target.z;
                    if (target.y < other.target.y + entry.getKey().getBbHeight() + padding
                            && other.target.y < target.y + mob.getBbHeight() + padding
                            && dx * dx + dz * dz < spacing * spacing) {
                        occupied = true;
                        break;
                    }
                }
                if (occupied) continue;
                double travel = mob.position().distanceToSqr(target);
                // 先选更靠近玩偶的安全圈，同半径再选路程更短者；已有有效站位不重新分配。
                if (radialDistance < bestRadius - 1.0e-6 || Math.abs(radialDistance - bestRadius) <= 1.0e-6 && travel < bestTravel) {
                    best = target;
                    bestRadius = radialDistance;
                    bestTravel = travel;
                }
            }
        }
        return best;
    }

    private static AABB boxAt(Mob mob, Vec3 target) {
        return mob.getBoundingBox().move(target.subtract(mob.position()));
    }

    private static boolean safe(ServerLevel level, Mob mob, Vec3 target) {
        AABB box = boxAt(mob, target);
        if (!level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                || !level.hasChunkAt(BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                || !level.getWorldBorder().isWithinBounds(box)
                || level.getBlockCollisions(mob, box).iterator().hasNext()) return false;
        if (mob.getNavigation() instanceof WaterBoundPathNavigation) {
            return level.getFluidState(BlockPos.containing(target)).is(FluidTags.WATER);
        }
        return mob.getNavigation() instanceof FlyingPathNavigation
                || level.getBlockCollisions(mob, box.move(0, -0.1, 0)).iterator().hasNext();
    }

    static void releaseDoll(Level level, BlockPos pos) {
        CONTROLLED.entrySet().removeIf(entry -> {
            if (entry.getKey().level() == level && entry.getValue().owner.equals(pos)) {
                release(entry.getKey(), entry.getValue());
                return true;
            }
            return false;
        });
    }

    static void releaseLevel(Level level) {
        CONTROLLED.entrySet().removeIf(entry -> {
            if (entry.getKey().level() == level) {
                release(entry.getKey(), entry.getValue());
                return true;
            }
            return false;
        });
    }

    private static void release(Mob mob, Control control) {
        if (control.path != null && mob.getNavigation().getPath() == control.path) {
            mob.getNavigation().stop();
        }
    }

    private static final class Control {
        private final BlockPos owner;
        private Path path;
        private long nextPathTick;
        private long nextSlotTick;
        private Vec3 target;
        private float width;
        private float height;

        private Control(BlockPos owner) {
            this.owner = owner.immutable();
        }
    }
}
