package com.friendswine;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 三种非敌对生物共用寻路、常规伤害免疫及原版装备保存／同步；仅跟随玩偶持有者时提速。 */
public final class PartyGuestEntity extends PathfinderMob {
    // 跟随目标仅在当前加载会话中保留；重载时按附近玩家重新选择，无需新增存档字段。
    // shortcut: 沿用已加载区块中的普通寻路，不传送、不跨维度；需要远距离召回时再扩展。
    private UUID followedPlayer;

    public PartyGuestEntity(EntityType<? extends PartyGuestEntity> type, Level level) {
        super(type, level);
        setDropChance(EquipmentSlot.MAINHAND, 2.0F);
    }

    /** 从既有实体 ID 选择角色；枚举不参与存档，旧香澄和艾玛实体无需迁移。 */
    public GuestVariant variant() {
        if (getType() == FriendsWine.AMIYA.get()) return GuestVariant.AMIYA;
        return getType() == FriendsWine.EMMA.get() ? GuestVariant.EMMA : GuestVariant.KASUMI;
    }
    public boolean isEmma() { return variant() == GuestVariant.EMMA; }

    /** 所有常规伤害都免疫；只保留 /kill 与虚空，避免管理员无法清理实体。 */
    @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
        return (!source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD))
                || super.isInvulnerableTo(source);
    }
    @Override public boolean isAttackable() { return false; }
    // 爆炸推力独立于受伤成功与否，必须同时跳过爆炸处理；不修改方块或其他实体。
    @Override public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }
    // 横扫等路径可先施加击退再判断伤害，因此不能只依赖伤害免疫。
    @Override public void knockback(double strength, double x, double z) {}

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

    /** 播放中的玩偶优先接管寻路；仅未携带玩偶时跟随背包持有者并加速，携带后仍沿用放置流程。 */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 10 != 0 || !(level() instanceof ServerLevel level)) return;
        // 活动玩偶的吸引控制器接管寻路，偷窃提速不能影响蹦迪。
        if (DollBlockEntity.nearestActive(level, position(), 50) != null) return;
        if (isCarryingDoll()) {
            getNavigation().stop();
            tryPlaceCarried(level);
            return;
        }
        Player holder = followTarget();
        if (holder != null) {
            getLookControl().setLookAt(holder, 30, 30);
            if (distanceToSqr(holder) <= 2.25 && hasLineOfSight(holder)) {
                getNavigation().stop();
                trySteal(holder);
            } else {
                // 背包或手持玩偶触发的跟随统一为两倍倍率，基础属性及蹦迪速度保持原值。
                getNavigation().moveTo(holder, 2.0);
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

    /** 每次检查优先选择 16 格内更近的持有者；没有更近者时，允许继续跟随范围外的有效原目标。 */
    private Player followTarget() {
        Player current = followedPlayer == null ? null : level().getPlayerByUUID(followedPlayer);
        Player nearest = current != null && canFollow(current) ? current : null;
        double distance = nearest == null ? 16 * 16 : distanceToSqr(nearest);
        for (Player player : level().players()) {
            double next = distanceToSqr(player);
            // 只有严格更近才切换，等距时保留当前目标，避免每次检查在两名玩家之间来回跳转。
            if (next <= 16 * 16 && canFollow(player) && (nearest == null || next < distance)) {
                nearest = player;
                distance = next;
            }
        }
        followedPlayer = nearest == null ? null : nearest.getUUID();
        return nearest;
    }

    /** 检查背包／快捷栏及双手；创造玩家也能吸引跟随，但偷取仍由独立的手持条件限制。 */
    private static boolean canFollow(Player player) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return false;
        if (player.getMainHandItem().is(FriendsWine.DOLL_ITEM.get())
                || player.getOffhandItem().is(FriendsWine.DOLL_ITEM.get())) return true;
        // 只检查直接物品槽，不解析潜影盒或收纳袋中的嵌套内容，也不要求默认名称／组件。
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(FriendsWine.DOLL_ITEM.get())) return true;
        }
        return false;
    }

    private static boolean canStealFrom(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.getAbilities().instabuild
                && (player.getMainHandItem().is(FriendsWine.DOLL_ITEM.get())
                || player.getOffhandItem().is(FriendsWine.DOLL_ITEM.get()));
    }

    public boolean isCarryingDoll() {
        return getMainHandItem().is(FriendsWine.DOLL_ITEM.get());
    }

    /** 校验偷取条件并转移一件玩偶；成功后广播一次短音效，失败不播放。 */
    boolean trySteal(Player player) {
        if (!(level() instanceof ServerLevel level) || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || isCarryingDoll() || !canStealFrom(player) || distanceToSqr(player) > 2.25
                || !hasLineOfSight(player) || DollBlockEntity.nearestActive(level, position(), 50) != null) return false;
        InteractionHand hand = player.getMainHandItem().is(FriendsWine.DOLL_ITEM.get())
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        setItemSlot(EquipmentSlot.MAINHAND, player.getItemInHand(hand).split(1));
        setDropChance(EquipmentSlot.MAINHAND, 2.0F);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        // 只在服务端完成物品转移后播放，避免失败偷取或客户端同步重复触发。
        playSound(FriendsWine.GUEST_STEAL.get(), 1.0F, 1.0F);
        return true;
    }

    boolean tryPlaceCarried(ServerLevel level) {
        if (!isCarryingDoll() || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
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
        // 跟随期间不能因玩家拉开距离而自然消失；目标失效后由 AI 清除锁定，恢复原消失规则。
        return followedPlayer == null && !isCarryingDoll() && super.removeWhenFarAway(distance);
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        // A player's stolen item is returned even when ordinary mob loot is disabled.
        if (isCarryingDoll()) {
            ItemStack carried = getMainHandItem();
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            spawnAtLocation(carried);
        }
    }

    public static boolean checkNaturalSpawn(EntityType<PartyGuestEntity> type, ServerLevelAccessor level,
                                            MobSpawnType reason, BlockPos pos, RandomSource random) {
        return level.getLevel().dimension().equals(Level.OVERWORLD)
                && level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && (MobSpawnType.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 8)
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

    /** 进入第三档时，以玩偶中心 16 格范围统计三种生物，并尝试在已加载的安全位置生成。 */
    public static void spawnGuests(ServerLevel level, BlockPos dollPos) {
        Vec3 center = Vec3.atCenterOf(dollPos);
        int existing = level.getEntitiesOfClass(PartyGuestEntity.class, new AABB(dollPos).inflate(16),
                guest -> guest.isAlive() && guest.position().distanceToSqr(center) <= 16 * 16).size();
        // 随机目标为 2～4 只，以共同上限 4 裁减；自然生成和生成蛋也占用名额。
        int wanted = Math.min(4 - existing, 2 + level.random.nextInt(3));
        for (int count = 0; count < wanted; count++) {
            // 三种角色等概率选择，统一计入前面的追加上限。
            EntityType<PartyGuestEntity> type = switch (GuestVariant.values()[level.random.nextInt(3)]) {
                case KASUMI -> FriendsWine.KASUMI.get();
                case EMMA -> FriendsWine.EMMA.get();
                case AMIYA -> FriendsWine.AMIYA.get();
            };
            boolean spawned = false;
            // 每只最多尝试 32 个随机位置，找不到安全落点时放弃，不强制加载区块。
            for (int attempt = 0; attempt < 32 && !spawned; attempt++) {
                int dx = level.random.nextInt(17) - 8, dz = level.random.nextInt(17) - 8;
                if (dx * dx + dz * dz < 9 || dx * dx + dz * dz > 64) continue;
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    BlockPos pos = dollPos.offset(dx, dy, dz);
                    AABB box = new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
                            pos.getX() + 0.8, pos.getY() + 1, pos.getZ() + 0.8);
                    if (!safeGround(level, pos, box)) continue;
                    PartyGuestEntity guest = type.create(level);
                    if (guest == null) break;
                    guest.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360, 0);
                    spawned = level.addFreshEntity(guest);
                    break;
                }
            }
        }
    }
}
