package com.friendswine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Both guests use vanilla navigation and saved/synced hand equipment. */
public final class PartyGuestEntity extends PathfinderMob {
    public PartyGuestEntity(EntityType<? extends PartyGuestEntity> type, Level level) {
        super(type, level);
        setDropChance(EquipmentSlot.MAINHAND, 2.0F);
    }

    public boolean isEmma() {
        return getType() == FriendsWine.EMMA.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep(ServerLevel serverLevel) {
        super.customServerAiStep(serverLevel);
        if (tickCount % 10 != 0 || !(level() instanceof ServerLevel level)) return;
        // The existing attraction controller owns navigation while a doll plays.
        if (DollBlockEntity.nearestActive(level, position(), 50) != null) return;
        if (isCarryingDoll()) {
            getNavigation().stop();
            tryPlaceCarried(level);
            return;
        }
        Player holder = nearestHolder();
        if (holder != null) {
            getLookControl().setLookAt(holder, 30, 30);
            if (distanceToSqr(holder) <= 2.25 && hasLineOfSight(holder)) {
                getNavigation().stop();
                trySteal(holder);
            } else {
                getNavigation().moveTo(holder, 1.0);
            }
            return;
        }
        DollBlockEntity doll = DollBlockEntity.nearestLoaded(level, position(), 50);
        if (doll != null) {
            Vec3 target = Vec3.atBottomCenterOf(doll.getBlockPos());
            if (position().distanceToSqr(target) > 2.25) {
                getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            } else {
                getNavigation().stop();
            }
        } else {
            getNavigation().stop();
        }
    }

    private Player nearestHolder() {
        Player nearest = null;
        double distance = 16 * 16;
        for (Player player : level().players()) {
            double next = distanceToSqr(player);
            if (canStealFrom(player) && next <= distance) {
                nearest = player;
                distance = next;
            }
        }
        return nearest;
    }

    private static boolean canStealFrom(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.getAbilities().instabuild
                && (player.getMainHandItem().is(FriendsWine.DOLL_ITEM.get())
                || player.getOffhandItem().is(FriendsWine.DOLL_ITEM.get()));
    }

    public boolean isCarryingDoll() {
        return getMainHandItem().is(FriendsWine.DOLL_ITEM.get());
    }

    boolean trySteal(Player player) {
        if (!(level() instanceof ServerLevel level) || !level.getGameRules().get(GameRules.MOB_GRIEFING)
                || isCarryingDoll() || !canStealFrom(player) || distanceToSqr(player) > 2.25
                || !hasLineOfSight(player) || DollBlockEntity.nearestActive(level, position(), 50) != null) return false;
        InteractionHand hand = player.getMainHandItem().is(FriendsWine.DOLL_ITEM.get())
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        setItemSlot(EquipmentSlot.MAINHAND, player.getItemInHand(hand).split(1));
        setDropChance(EquipmentSlot.MAINHAND, 2.0F);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        return true;
    }

    boolean tryPlaceCarried(ServerLevel level) {
        if (!isCarryingDoll() || !level.getGameRules().get(GameRules.MOB_GRIEFING)) return false;
        BlockPos origin = blockPosition();
        // ponytail: a bounded nearby search; travel to distant building sites is outside this mob's behavior.
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius || dx * dx + dz * dz > 16) continue;
                    for (int dy : new int[]{0, -1, 1, -2, 2}) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > 16
                                || !safeGround(level, pos, new AABB(pos))) continue;
                        var sight = level.clip(new ClipContext(getEyePosition(), Vec3.atCenterOf(pos),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                        if (sight.getType() != HitResult.Type.MISS && !sight.getBlockPos().equals(pos)) continue;
                        ItemStack carried = getMainHandItem();
                        ItemStack placing = carried.copy();
                        placing.setCount(1);
                        BlockState previous = level.getBlockState(pos);
                        var context = new DirectionalPlaceContext(level, pos, getDirection(), placing, Direction.UP);
                        try {
                            boolean placed = ((BlockItem) placing.getItem()).place(context).consumesAction();
                            if (!placed || !context.getClickedPos().equals(pos)
                                    || !(level.getBlockEntity(pos) instanceof DollBlockEntity doll)) {
                                restoreFailedPlacement(level, pos, previous);
                                continue;
                            }
                            doll.stopPlaying();
                            if (!doll.startPlaying() || doll.getMode() != DollBlockEntity.FULL) {
                                restoreFailedPlacement(level, pos, previous);
                                continue;
                            }
                            carried.shrink(1);
                            if (carried.isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                            return true;
                        } catch (RuntimeException failure) {
                            restoreFailedPlacement(level, pos, previous);
                            com.mojang.logging.LogUtils.getLogger().warn("Party guest could not place its doll at {}", pos, failure);
                            return false;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void restoreFailedPlacement(ServerLevel level, BlockPos pos, BlockState previous) {
        if (level.getBlockState(pos).is(FriendsWine.DOLL.get())) level.setBlock(pos, previous, 3);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isCarryingDoll() && super.removeWhenFarAway(distance);
    }

    @Override
    protected void dropEquipment(ServerLevel serverLevel) {
        super.dropEquipment(serverLevel);
        // A player's stolen item is returned even when ordinary mob loot is disabled.
        if (isCarryingDoll()) {
            ItemStack carried = getMainHandItem();
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            spawnAtLocation(serverLevel, carried);
        }
    }

    public static boolean checkNaturalSpawn(EntityType<PartyGuestEntity> type, ServerLevelAccessor level,
                                            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getLevel().dimension().equals(Level.OVERWORLD)
                && level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && (EntitySpawnReason.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 8)
                && level.getFluidState(pos).isEmpty();
    }

    private static boolean safeGround(ServerLevel level, BlockPos pos, AABB box) {
        return level.hasChunkAt(pos) && level.hasChunkAt(pos.above()) && level.hasChunkAt(pos.below())
                && level.getWorldBorder().isWithinBounds(box)
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && !dangerousGround(level.getBlockState(pos.below())) && !dangerousGround(level.getBlockState(pos))
                && !dangerousGround(level.getBlockState(pos.above())) && level.getBlockEntity(pos) == null
                && level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty()
                && !level.getBlockCollisions(null, box).iterator().hasNext()
                && level.getEntities((net.minecraft.world.entity.Entity) null, box,
                        entity -> entity.isAlive() && !entity.isSpectator()).isEmpty();
    }

    private static boolean dangerousGround(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(BlockStateProperties.LIT));
    }

    /** 进入第三档时，以玩偶中心 16 格范围统计两种生物，并尝试在已加载的安全位置生成。 */
    public static void spawnGuests(ServerLevel level, BlockPos dollPos) {
        Vec3 center = Vec3.atCenterOf(dollPos);
        int existing = level.getEntitiesOfClass(PartyGuestEntity.class, new AABB(dollPos).inflate(16),
                guest -> guest.isAlive() && guest.position().distanceToSqr(center) <= 16 * 16).size();
        // 随机目标为 2～4 只，以共同上限 4 裁减；自然生成和生成蛋也占用名额。
        int wanted = Math.min(4 - existing, 2 + level.getRandom().nextInt(3));
        for (int count = 0; count < wanted; count++) {
            EntityType<PartyGuestEntity> type = level.getRandom().nextBoolean() ? FriendsWine.KASUMI.get() : FriendsWine.EMMA.get();
            boolean spawned = false;
            // 每只最多尝试 32 个随机位置，找不到安全落点时放弃，不强制加载区块。
            for (int attempt = 0; attempt < 32 && !spawned; attempt++) {
                int dx = level.getRandom().nextInt(17) - 8, dz = level.getRandom().nextInt(17) - 8;
                if (dx * dx + dz * dz < 9 || dx * dx + dz * dz > 64) continue;
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    BlockPos pos = dollPos.offset(dx, dy, dz);
                    AABB box = new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
                            pos.getX() + 0.8, pos.getY() + 1, pos.getZ() + 0.8);
                    if (!safeGround(level, pos, box)) continue;
                    PartyGuestEntity guest = type.create(level, EntitySpawnReason.TRIGGERED);
                    if (guest == null) break;
                    guest.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
                    spawned = level.addFreshEntity(guest);
                    break;
                }
            }
        }
    }
}
