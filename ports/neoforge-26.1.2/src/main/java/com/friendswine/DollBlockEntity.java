package com.friendswine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class DollBlockEntity extends BlockEntity {
    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();
    private long startTick = -1;
    public static final int STOPPED = 0;
    public static final int SQUASH_ONLY = 1;
    public static final int FULL = 2;
    public static final int ORBIT = 3;
    private int mode = STOPPED;
    private long rotationStartTick = -1;

    public DollBlockEntity(BlockPos pos, BlockState state) {
        super(FriendsWine.DOLL_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isPlaying() {
        return mode != STOPPED && !isRemoved() && level != null && startTick >= 0
                && level.getGameTime() >= startTick;
    }

    public long getStartTick() {
        return startTick;
    }

    public int getMode() {
        return isPlaying() ? mode : STOPPED;
    }

    public boolean isRotating() {
        return getMode() == FULL || getMode() == ORBIT;
    }

    public long getRotationElapsedTicks(long gameTime) {
        return rotationStartTick < 0 ? 0 : Math.max(0, gameTime - rotationStartTick);
    }

    public long getElapsedTicks(long gameTime) {
        return startTick < 0 ? 0 : Math.max(0, gameTime - startTick);
    }

    public boolean startPlaying() {
        return startPlaying(FULL);
    }

    private boolean startPlaying(int nextMode) {
        if (level == null || level.isClientSide() || isRemoved() || isPlaying()) {
            return false;
        }
        startTick = level.getGameTime();
        mode = nextMode;
        rotationStartTick = nextMode == FULL ? startTick : -1;
        registerLoaded();
        sync();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) PartyGuestEntity.spawnGuests(serverLevel, worldPosition);
        return true;
    }

    public void cycleRemote() {
        if (level == null || level.isClientSide() || isRemoved()) return;
        if (!isPlaying()) {
            startPlaying(SQUASH_ONLY);
        } else if (mode == SQUASH_ONLY) {
            mode = FULL;
            rotationStartTick = level.getGameTime();
            // Keep the song and squash clock unchanged, including the current sound instance.
            sync();
        } else if (mode == FULL) {
            mode = ORBIT;
            sync();
        } else {
            stopPlaying();
        }
    }

    public void stopPlaying() {
        if (startTick < 0) {
            return;
        }
        startTick = -1;
        rotationStartTick = -1;
        mode = STOPPED;
        if (level != null && !level.isClientSide()) {
            DollAttraction.releaseDoll(level, worldPosition);
            sync();
        }
    }

    private void sync() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putLong("StartTick", startTick);
        tag.putInt("Mode", mode);
        tag.putLong("RotationStartTick", rotationStartTick);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        startTick = tag.getLongOr("StartTick", -1);
        // Old saves only have StartTick and always used the full animation.
        mode = tag.getIntOr("Mode", startTick >= 0 ? FULL : STOPPED);
        rotationStartTick = tag.getLongOr("RotationStartTick", startTick);
        if (mode < SQUASH_ONLY || mode > ORBIT || startTick < 0) {
            mode = STOPPED;
            startTick = -1;
            rotationStartTick = -1;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        registerLoaded();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        registerLoaded();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unregisterLoaded();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterLoaded();
        super.onChunkUnloaded();
    }

    private void registerLoaded() {
        if (level != null && !isRemoved()) {
            synchronized (LOADED) {
                LOADED.computeIfAbsent(level, unused -> new HashSet<>()).add(worldPosition.immutable());
            }
        }
    }

    private void unregisterLoaded() {
        if (level == null) {
            return;
        }
        synchronized (LOADED) {
            Set<BlockPos> positions = LOADED.get(level);
            if (positions != null) {
                positions.remove(worldPosition);
                if (positions.isEmpty()) {
                    LOADED.remove(level);
                }
            }
        }
        if (!level.isClientSide()) {
            DollAttraction.releaseDoll(level, worldPosition);
        }
    }

    static void forgetLevel(Level level) {
        synchronized (LOADED) {
            LOADED.remove(level);
        }
    }

    /** Call on the owning level's thread; only the position registry is shared between sides. */
    public static List<DollBlockEntity> getActiveDolls(Level level) {
        List<BlockPos> positions;
        synchronized (LOADED) {
            positions = List.copyOf(LOADED.getOrDefault(level, Set.of()));
        }
        List<DollBlockEntity> active = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof DollBlockEntity doll && doll.isPlaying()) {
                active.add(doll);
            }
        }
        return active;
    }

    @Nullable
    public static DollBlockEntity nearestLoaded(Level level, Vec3 position, double radius) {
        List<BlockPos> positions;
        synchronized (LOADED) { positions = List.copyOf(LOADED.getOrDefault(level, Set.of())); }
        DollBlockEntity nearest = null;
        double nearestDistance = radius * radius;
        for (BlockPos pos : positions) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof DollBlockEntity doll) || doll.isRemoved()) continue;
            double distance = position.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance <= nearestDistance && (nearest == null || distance < nearestDistance || pos.asLong() < nearest.worldPosition.asLong())) {
                nearest = doll;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    @Nullable
    public static DollBlockEntity nearestActive(Level level, Vec3 position, double radius) {
        DollBlockEntity nearest = null;
        double nearestDistance = radius * radius;
        for (DollBlockEntity doll : getActiveDolls(level)) {
            double distance = position.distanceToSqr(Vec3.atCenterOf(doll.worldPosition));
            if (distance <= nearestDistance && (nearest == null || distance < nearestDistance
                    || doll.worldPosition.asLong() < nearest.worldPosition.asLong())) {
                nearest = doll;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
