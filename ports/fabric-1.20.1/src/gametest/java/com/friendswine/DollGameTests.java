package com.friendswine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Exercises registered blocks, server ticks, navigation, and vanilla collision in a real level. */
public final class DollGameTests {
    private static final BlockPos DOLL_POS = new BlockPos(4, 1, 12);

    public DollGameTests() {}

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_range", batch = "friendswine-playback", timeoutTicks = 2400)
    public static void playbackAndAttraction(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 63, 0, 23)) {
            helper.setBlock(pos, Blocks.STONE);
        }
        // A tall wall separates the northern pig from the doll. The open lane is south.
        for (BlockPos pos : BlockPos.betweenClosed(0, 1, 6, 63, 4, 6)) {
            helper.setBlock(pos, Blocks.STONE);
        }
        helper.setBlock(DOLL_POS, FriendsWine.DOLL.get());
        DollBlockEntity doll = ((DollBlockEntity) helper.getBlockEntity(DOLL_POS));
        helper.assertFalse(doll.isPlaying(), "A newly placed doll must be idle");
        helper.assertTrue(doll.getStartTick() == -1, "A newly placed doll must not have a start time");

        ServerPlayer player = WineGameTests.mockServerPlayer(helper);
        Vec3 playerStart = helper.absoluteVec(new Vec3(10.5, 1, 18.5));
        player.moveTo(playerStart.x, playerStart.y, playerStart.z, 0, 0);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);

        Pig outside = pig(helper, new Vec3(55.5, 1, 12.5), false);
        Pig seeker = pig(helper, new Vec3(45.5, 1, 12.5), false);
        Pig pulled = pig(helper, new Vec3(4.5, 1, 20.5), true);
        Pig wallPig = pig(helper, new Vec3(4.5, 1, 4.5), true);
        Pig inside = pig(helper, new Vec3(4.5, 1, 9.5), true);
        Vec3 outsideStart = outside.position();
        Vec3 pulledStart = pulled.position();
        Vec3 wallStart = wallPig.position();
        Vec3 insideStart = inside.position();
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(DOLL_POS));
        long[] startTick = {-1};
        Vec3[] stoppedPullPosition = new Vec3[1];

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(doll.isPlaying(), "Idle dolls must remain idle across server ticks");
                    assertHorizontalStill(helper, pulled, pulledStart, "Idle dolls must not attract mobs");
                    click(helper, player);
                    helper.assertTrue(doll.isPlaying(), "Right-click must start playback");
                    startTick[0] = doll.getStartTick();
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(outside.getNavigation().isDone(), "A mob beyond 50 blocks must not get a path");
                    assertHorizontalStill(helper, outside, outsideStart, "A mob beyond 50 blocks must not move");
                    helper.assertTrue(seeker.getNavigation().getPath() != null && !seeker.getNavigation().isDone(),
                            "A mob between 10 and 50 blocks must receive a real navigation path");
                    helper.assertTrue(pulled.getDeltaMovement().dot(center.subtract(pulled.position())) > 0,
                            "A mob between 5 and 10 blocks must gain inward velocity");
                    helper.assertTrue(inside.getDeltaMovement().horizontalDistanceSqr() > 0,
                            "A mob already within five blocks must still approach its spread-out slot");

                    clickWithItem(helper, player);
                    helper.assertFalse(doll.isPlaying(), "A second right-click with an item must immediately stop playback");
                    helper.assertTrue(doll.getStartTick() == -1, "Manual stop must clear the playback start time");
                    helper.assertTrue(DollBlockEntity.nearestActive(helper.getLevel(), center, 20) == null,
                            "Manual stop must immediately clear the active-doll lookup");
                    helper.assertTrue(seeker.getNavigation().isDone(), "Manual stop must immediately release the owned navigation path");
                    // Remove existing momentum so subsequent movement would expose a new attraction force.
                    pulled.setDeltaMovement(Vec3.ZERO);
                    stoppedPullPosition[0] = pulled.position();
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(doll.isPlaying(), "A manually stopped doll must remain idle");
                    assertHorizontalStill(helper, pulled, stoppedPullPosition[0], "A stopped doll must not resume attracting mobs");
                    helper.assertTrue(seeker.getNavigation().isDone(), "A stopped doll must not recreate its navigation path");
                    click(helper, player);
                    helper.assertTrue(doll.isPlaying(), "An empty-hand right-click must restart stopped playback");
                    helper.assertTrue(doll.getStartTick() == helper.getLevel().getGameTime()
                                    && doll.getStartTick() > startTick[0],
                            "Restarting must record a new start time at the current game tick");
                    helper.assertTrue(doll.getElapsedTicks(helper.getLevel().getGameTime()) == 0,
                            "Restarted playback must begin at elapsed tick zero");
                })
                .thenIdle(35)
                .thenExecute(() -> {
                    helper.assertTrue(pulled.getZ() < pulledStart.z - 0.3,
                            "Attraction must physically move a mob whose walking speed is zero");
                    helper.assertTrue(wallPig.getZ() > wallStart.z + 0.3,
                            "The wall test mob must actually move toward the wall");
                    double wallFace = helper.absolutePos(new BlockPos(4, 1, 6)).getZ();
                    helper.assertTrue(wallPig.getBoundingBox().maxZ <= wallFace + 1.0e-5,
                            "Attraction must not move a mob through a solid wall");
                    helper.assertTrue(seeker.position().distanceTo(center) < 40,
                            "A mob in the outer ring must follow its path toward the doll");
                    helper.assertTrue(inside.position().distanceToSqr(insideStart) > 0.09,
                            "The inner-ring mob must physically move to its formation slot");
                    assertHorizontalStill(helper, outside, outsideStart, "The out-of-range mob must remain still");
                    helper.assertTrue(player.getDeltaMovement().lengthSqr() < 1.0e-8
                                    && player.position().distanceToSqr(playerStart) < 1.0e-8,
                            "A real player in the pull radius must not receive attraction");
                    // Give removal a live owned path to release, even if this pig already reached the inner ring.
                    Vec3 restart = helper.absoluteVec(new Vec3(18.5, 1, 12.5));
                    seeker.moveTo(restart.x, restart.y, restart.z, 0, 0);
                    seeker.setDeltaMovement(Vec3.ZERO);
                    seeker.getNavigation().stop();
                })
                // Repathing is throttled to 10 ticks, and teleporting can briefly clear onGround.
                // Wait for an actual owned path rather than requiring one after an arbitrary 2 ticks.
                .thenWaitUntil(() -> helper.assertTrue(!seeker.getNavigation().isDone(),
                        "Removal must be tested against a live owned path"))
                .thenExecute(() -> {
                    helper.destroyBlock(DOLL_POS);
                    helper.assertTrue(doll.isRemoved() && !doll.isPlaying(), "Removing the doll must stop it");
                    helper.assertTrue(DollBlockEntity.nearestActive(helper.getLevel(), center, 20) == null,
                            "The removed doll must leave the active-doll registry");
                    helper.assertTrue(seeker.getNavigation().isDone(), "Removing the owner must release its navigation path");
                    pulled.setDeltaMovement(Vec3.ZERO);
                    helper.setBlock(DOLL_POS, FriendsWine.DOLL.get());
                    helper.assertFalse(((DollBlockEntity) helper.getBlockEntity(DOLL_POS)).isPlaying(),
                            "Replacing a removed doll must create an idle instance");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(pulled.getDeltaMovement().horizontalDistanceSqr() < 1.0e-8,
                            "Removed dolls must not keep applying force");
                    click(helper, player);
                    helper.assertTrue(((DollBlockEntity) helper.getBlockEntity(DOLL_POS)).isPlaying(),
                            "The replacement doll must be independently playable");
                })
                // Use real elapsed ticks, without changing world time or forging block-entity state.
                .thenIdle(2251)
                .thenExecute(() -> helper.assertTrue(((DollBlockEntity) helper.getBlockEntity(DOLL_POS)).isPlaying(),
                        "Playback must remain active across two song loops"))
                .thenIdle(2)
                .thenExecute(() -> {
                    DollBlockEntity ended = ((DollBlockEntity) helper.getBlockEntity(DOLL_POS));
                    helper.assertTrue(ended.isPlaying(), "Songs ending must not stop playback");
                    ended.stopPlaying();
                    helper.assertTrue(ended.getStartTick() == -1, "Manual stop must clear the continuous playback clock");
                    helper.assertTrue(DollBlockEntity.nearestActive(helper.getLevel(), center, 20) == null,
                            "Manual stop must release the active-doll lookup");
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_empty", batch = "friendswine-remote", timeoutTicks = 1180)
    public static void remoteStages(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        helper.setBlock(DOLL_POS, FriendsWine.DOLL.get());
        DollBlockEntity doll = ((DollBlockEntity) helper.getBlockEntity(DOLL_POS));
        ServerPlayer player = WineGameTests.mockServerPlayer(helper);
        player.setNoGravity(true);
        ItemStack remote = new ItemStack(FriendsWine.REMOTE.get());
        ItemStack second = new ItemStack(FriendsWine.REMOTE.get());
        long[] start = {-1};
        helper.startSequence()
                .thenExecute(() -> {
                    remoteClick(player, remote); PartyGuestGameTests.clean(helper);
                    helper.assertFalse(doll.isPlaying(), "Unbound remotes must not start dolls");
                    bind(helper, player, remote);
                    helper.assertFalse(doll.isPlaying(), "Binding must not toggle playback");
                    helper.assertTrue(player.isUsingItem(), "Binding must latch held use");
                    ItemStack restored = ItemStack.of(remote.save(new CompoundTag()));
                    helper.assertTrue(restored.getTag().equals(remote.getTag()),
                            "Binding must survive item serialization");
                    bind(helper, player, second);
                    remoteClick(player, remote); PartyGuestGameTests.clean(helper);
                    helper.assertTrue(doll.getMode() == DollBlockEntity.SQUASH_ONLY,
                            "First click must start squash without rotation");
                    start[0] = doll.getStartTick();
                    FriendsWine.REMOTE.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                    helper.assertTrue(doll.getMode() == DollBlockEntity.SQUASH_ONLY,
                            "Held use must not advance another stage");
                })
                .thenIdle(7)
                .thenExecute(() -> {
                    remoteClick(player, second); PartyGuestGameTests.clean(helper);
                    helper.assertTrue(doll.getMode() == DollBlockEntity.FULL && doll.getStartTick() == start[0]
                                    && doll.getElapsedTicks(helper.getLevel().getGameTime()) == 7
                                    && doll.getRotationElapsedTicks(helper.getLevel().getGameTime()) == 0,
                            "Another remote must enable rotation without restarting the song or squash clock");
                    bind(helper, player, remote);
                    helper.assertTrue(doll.getMode() == DollBlockEntity.FULL && doll.getStartTick() == start[0],
                            "Rebinding an active doll must preserve its phase and clock");
                    remoteClick(player, remote); PartyGuestGameTests.clean(helper);
                    helper.assertTrue(doll.getMode() == DollBlockEntity.ORBIT && doll.getStartTick() == start[0]
                                    && doll.isRotating(), "Third click adds orbit without resetting playback or rotation");
                    CompoundTag orbitSave = doll.getUpdateTag();
                    DollBlockEntity orbitRestored = new DollBlockEntity(doll.getBlockPos(), doll.getBlockState());
                    orbitRestored.setLevel(helper.getLevel());
                    orbitRestored.load(orbitSave);
                    helper.assertTrue(orbitRestored.getMode() == DollBlockEntity.ORBIT
                                    && orbitRestored.getRotationElapsedTicks(helper.getLevel().getGameTime())
                                    == doll.getRotationElapsedTicks(helper.getLevel().getGameTime()),
                            "Orbit mode and both clocks survive serialization");
                    FriendsWine.REMOTE.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                    helper.assertTrue(doll.getMode() == DollBlockEntity.ORBIT, "Held use must not exit orbit");
                    remoteClick(player, second); PartyGuestGameTests.clean(helper);
                    helper.assertTrue(doll.getMode() == DollBlockEntity.STOPPED && doll.getStartTick() == -1,
                            "Fourth click must stop and reset playback");
                    player.stopUsingItem();
                    click(helper, player);
                    helper.assertTrue(doll.getMode() == DollBlockEntity.FULL, "Ordinary right-click starts the full effect");
                    remoteClick(player, second); PartyGuestGameTests.clean(helper);
                    helper.assertTrue(doll.getMode() == DollBlockEntity.ORBIT,
                            "Remote must add orbit to ordinary right-click's actual full state");
                    remoteClick(player, second); PartyGuestGameTests.clean(helper);
                    helper.assertFalse(doll.isPlaying(), "The next remote click must stop the shared orbit state");

                    // Loading an old save with only StartTick restores the original full mode.
                    CompoundTag legacy = new CompoundTag();
                    legacy.putLong("StartTick", helper.getLevel().getGameTime());
                    DollBlockEntity restored = new DollBlockEntity(doll.getBlockPos(), doll.getBlockState());
                    restored.setLevel(helper.getLevel());
                    restored.load(legacy);
                    helper.assertTrue(restored.getMode() == DollBlockEntity.FULL, "Old saves must retain full playback");

                    player.stopUsingItem();
                    player.setItemInHand(InteractionHand.MAIN_HAND, second);
                    RemoteItem.bind(helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER),
                            doll.getBlockPos(), player, InteractionHand.MAIN_HAND);
                    remoteClick(player, second); PartyGuestGameTests.clean(helper);
                    helper.assertFalse(doll.isPlaying(), "Cross-dimension remotes must not operate a doll");
                    BlockPos unloaded = new BlockPos(29000000, 100, 29000000);
                    player.stopUsingItem();
                    RemoteItem.bind(helper.getLevel(), unloaded, player, InteractionHand.MAIN_HAND);
                    remoteClick(player, second); PartyGuestGameTests.clean(helper);
                    helper.assertFalse(helper.getLevel().hasChunkAt(unloaded), "Remote use must not load target chunks");
                    helper.assertFalse(doll.isPlaying(), "Unavailable targets must not change another doll");
                    bind(helper, player, second);
                    helper.destroyBlock(DOLL_POS);
                    remoteClick(player, second); PartyGuestGameTests.clean(helper);
                    helper.assertFalse(doll.isPlaying(), "Removed targets must remain stopped");
                    helper.setBlock(DOLL_POS, FriendsWine.DOLL.get());
                    bind(helper, player, remote);
                    Vec3 far = helper.absoluteVec(new Vec3(100.5, 20, 100.5));
                    player.moveTo(far.x, far.y, far.z, 0, 0);
                    remoteClick(player, remote); PartyGuestGameTests.clean(helper);
                    DollBlockEntity active = ((DollBlockEntity) helper.getBlockEntity(DOLL_POS));
                    helper.assertTrue(active.getMode() == DollBlockEntity.SQUASH_ONLY,
                            "A loaded doll must be remotely controllable beyond attraction range");
                    CompoundTag saved = active.getUpdateTag();
                    restored.load(saved);
                    helper.assertTrue(restored.getMode() == DollBlockEntity.SQUASH_ONLY && restored.getStartTick() == active.getStartTick(),
                            "The saved mode and song clock must round-trip");
                })
                .thenIdle(1127)
                .thenExecute(() -> {
                    helper.assertTrue(((DollBlockEntity) helper.getBlockEntity(DOLL_POS)).getMode() == DollBlockEntity.SQUASH_ONLY,
                            "Squash-only mode must continue after the song loops");
                    remoteClick(player, remote); PartyGuestGameTests.clean(helper);
                    helper.assertTrue(((DollBlockEntity) helper.getBlockEntity(DOLL_POS)).getMode() == DollBlockEntity.FULL,
                            "The next click after a song loops must add rotation without restarting");
                    ((DollBlockEntity) helper.getBlockEntity(DOLL_POS)).stopPlaying();
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    @GameTest(template = "friendswine:doll_empty", batch = "friendswine-formation", timeoutTicks = 400)
    public static void distributedSlots(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 31, 0, 23)) helper.setBlock(pos, Blocks.STONE);
        BlockPos pos = new BlockPos(12, 1, 12);
        helper.setBlock(pos, FriendsWine.DOLL.get());
        DollBlockEntity doll = ((DollBlockEntity) helper.getBlockEntity(pos));
        Pig[] pigs = new Pig[12];
        for (int i = 0; i < pigs.length - 1; i++) {
            double angle = 2 * Math.PI * i / (pigs.length - 1);
            pigs[i] = pig(helper, new Vec3(12.5 + 8 * Math.cos(angle), 1, 12.5 + 8 * Math.sin(angle)), true);
        }
        pigs[pigs.length - 1] = pig(helper, new Vec3(14.5, 1, 12.5), true);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(pos));
        Vec3[] settled = new Vec3[pigs.length];
        helper.startSequence()
                .thenExecute(() -> { doll.startPlaying(); PartyGuestGameTests.clean(helper); })
                .thenWaitUntil(() -> {
                    for (Pig pig : pigs) {
                        double dx = pig.getX() - center.x, dz = pig.getZ() - center.z;
                        helper.assertTrue(dx * dx + dz * dz <= 25 && pig.getDeltaMovement().horizontalDistanceSqr() < 0.0001,
                                "All attracted pigs must settle inside the horizontal five-block disk");
                    }
                    for (int i = 0; i < pigs.length; i++) {
                        for (int j = i + 1; j < pigs.length; j++) {
                            helper.assertFalse(pigs[i].getBoundingBox().inflate(0.25).intersects(pigs[j].getBoundingBox()),
                                    "Settled pigs must occupy separate positions with clearance");
                        }
                        settled[i] = pigs[i].position();
                    }
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    for (int i = 0; i < pigs.length; i++) {
                        helper.assertTrue(pigs[i].position().distanceToSqr(settled[i]) < 0.04,
                                "Existing slots must remain stable without formation jitter");
                    }
                    doll.stopPlaying();
                    for (Pig pig : pigs) {
                        pig.setDeltaMovement(Vec3.ZERO);
                        helper.assertTrue(pig.getNavigation().isDone(), "Stopping must release formation navigation");
                    }
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    for (Pig pig : pigs) helper.assertTrue(pig.getDeltaMovement().horizontalDistanceSqr() < 1e-8,
                            "Stopped formation must not keep pulling mobs");
                })
                .thenSucceed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_empty", batch = "friendswine-orbit", timeoutTicks = 1200)
    public static void orbitAndRelease(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        // Repeated GameTest runs reuse the world; previous failed runs can leave nearby mobs.
        helper.getLevel().getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(12, 1, 12))).inflate(50))
                .forEach(Mob::discard);
        for (BlockPos floor : BlockPos.betweenClosed(0, 0, 0, 31, 0, 23)) helper.setBlock(floor, Blocks.STONE);
        BlockPos pos = new BlockPos(12, 1, 12);
        helper.setBlock(pos, FriendsWine.DOLL.get());
        DollBlockEntity doll = ((DollBlockEntity) helper.getBlockEntity(pos));
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(pos));
        Mob[] mobs = new Mob[6];
        Number originalSpeed = ServerConfig.ORBIT_SPEED.get();
        ServerConfig.ORBIT_SPEED.set(1.0);
        for (int i = 0; i < mobs.length; i++) {
            double angle = 2 * Math.PI * i / mobs.length;
            mobs[i] = i == 0 ? helper.spawn(EntityType.COW, new Vec3(12.5 + 7 * Math.cos(angle), 1, 12.5 + 7 * Math.sin(angle)))
                    : pig(helper, new Vec3(12.5 + 7 * Math.cos(angle), 1, 12.5 + 7 * Math.sin(angle)), true);
            mobs[i].removeFreeWill();
            mobs[i].getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        }
        ServerPlayer player = WineGameTests.mockServerPlayer(helper);
        player.setNoGravity(true);
        player.moveTo(center.add(0, 2, 0));
        Vec3 playerStart = player.position();
        double[] previous = new double[mobs.length], turns = new double[mobs.length];
        Vec3[] paused = new Vec3[mobs.length];
        boolean[] measuring = {false};
        helper.onEachTick(() -> {
            if (!measuring[0]) return;
            for (int i = 0; i < mobs.length; i++) {
                double x = mobs[i].getX() - center.x, z = mobs[i].getZ() - center.z;
                double angle = Math.atan2(z, x);
                turns[i] += Math.atan2(Math.sin(angle - previous[i]), Math.cos(angle - previous[i]));
                previous[i] = angle;
                helper.assertTrue(x * x + z * z <= 26, "Orbit must remain inside the five-block formation");
                helper.assertFalse(helper.getLevel().getBlockCollisions(mobs[i], mobs[i].getBoundingBox()).iterator().hasNext(),
                        "Orbit must respect native block collision");
            }
        });
        helper.startSequence().thenExecute(() -> { doll.startPlaying(); PartyGuestGameTests.clean(helper); }).thenWaitUntil(() -> {
            for (Mob mob : mobs) helper.assertTrue(mob.position().distanceToSqr(center) < 25
                            && mob.getDeltaMovement().horizontalDistanceSqr() < 0.0001,
                    "All mobs must settle before orbit starts");
        }).thenExecute(() -> {
            long song = doll.getStartTick();
            doll.cycleRemote(); PartyGuestGameTests.clean(helper);
            helper.assertTrue(doll.getMode() == DollBlockEntity.ORBIT && doll.getStartTick() == song,
                    "Orbit keeps the original music timeline");
            for (int i = 0; i < mobs.length; i++) previous[i] = Math.atan2(mobs[i].getZ() - center.z, mobs[i].getX() - center.x);
            measuring[0] = true;
        }).thenIdle(400).thenExecute(() -> {
            measuring[0] = false;
            for (double turn : turns) helper.assertTrue(turn < -5.5 && turn > -6.8,
                    "Each mob must physically complete about one counterclockwise turn in 400 ticks: " + turn);
            helper.assertTrue(player.position().distanceToSqr(playerStart) < 1e-8
                            && player.getDeltaMovement().lengthSqr() < 1e-8,
                    "Orbit must never move a player");
            ServerConfig.ORBIT_SPEED.set(0.0);
        }).thenIdle(40).thenExecute(() -> {
            for (int i = 0; i < mobs.length; i++) paused[i] = mobs[i].position();
        }).thenIdle(30).thenExecute(() -> {
            for (int i = 0; i < mobs.length; i++) helper.assertTrue(mobs[i].position().distanceToSqr(paused[i]) < 0.001,
                    "Zero orbit speed must pause physical orbit");
            helper.assertTrue(doll.isPlaying() && doll.isRotating(), "Pausing orbit keeps music and model effects active");
            ServerConfig.ORBIT_SPEED.set(2.0);
            for (int i = 0; i < mobs.length; i++) {
                turns[i] = 0;
                previous[i] = Math.atan2(mobs[i].getZ() - center.z, mobs[i].getX() - center.x);
            }
            measuring[0] = true;
        }).thenIdle(200).thenExecute(() -> {
            measuring[0] = false;
            for (double turn : turns) helper.assertTrue(turn < -5.5 && turn > -6.8,
                    "Double speed must complete about one physical turn in 200 ticks: " + turn);
            ServerConfig.ORBIT_SPEED.set(4.0);
            for (int i = 0; i < mobs.length; i++) {
                turns[i] = 0;
                previous[i] = Math.atan2(mobs[i].getZ() - center.z, mobs[i].getX() - center.x);
            }
            measuring[0] = true;
        }).thenIdle(100).thenExecute(() -> {
            measuring[0] = false;
            for (double turn : turns) helper.assertTrue(turn < -5.5 && turn > -6.8,
                    "Maximum speed must complete about one physical turn in 100 ticks: " + turn);
            ServerConfig.ORBIT_SPEED.set(1.0);
            CompoundTag saved = doll.getUpdateTag();
            doll.setRemoved();
            doll.load(saved);
            for (int i = 0; i < mobs.length; i++) {
                helper.assertTrue(mobs[i].getNavigation().isDone(), "Unloading releases orbit navigation immediately");
                mobs[i].moveTo(new Vec3(center.x + 7 * Math.cos(2 * Math.PI * i / mobs.length), center.y - 0.5,
                        center.z + 7 * Math.sin(2 * Math.PI * i / mobs.length)));
                mobs[i].setDeltaMovement(Vec3.ZERO);
            }
        }).thenIdle(5).thenExecute(() -> {
            for (Mob mob : mobs) helper.assertTrue(mob.getDeltaMovement().horizontalDistanceSqr() < 1e-8,
                    "Unloaded dolls must not apply orbit force: " + mob.getType() + " velocity=" + mob.getDeltaMovement() + " nearest=" + DollBlockEntity.nearestActive(mob.level(),mob.position(),50));
            doll.clearRemoved();
            helper.assertTrue(doll.getMode() == DollBlockEntity.ORBIT && doll.isRotating(),
                    "Reloading must retain orbit and self-rotation");
        }).thenIdle(60).thenExecute(() -> {
            helper.assertTrue(java.util.Arrays.stream(mobs).anyMatch(mob -> mob.getDeltaMovement().horizontalDistanceSqr() > 0.0001),
                    "Reloading resumes physical orbit");
            doll.cycleRemote(); PartyGuestGameTests.clean(helper);
            helper.assertFalse(doll.isPlaying(), "Fourth phase stops orbit");
            for (int i = 0; i < mobs.length; i++) {
                helper.assertTrue(mobs[i].getNavigation().isDone(), "Stop releases navigation");
                // Isolate release from vanilla entity pushing after the fast orbit.
                mobs[i].moveTo(new Vec3(center.x + 7 * Math.cos(2 * Math.PI * i / mobs.length), center.y - 0.5,
                        center.z + 7 * Math.sin(2 * Math.PI * i / mobs.length)));
                mobs[i].setDeltaMovement(Vec3.ZERO);
            }
        }).thenIdle(5).thenExecute(() -> {
            for (Mob mob : mobs) helper.assertTrue(mob.getDeltaMovement().horizontalDistanceSqr() < 1e-8, "Stopped orbit applies no force");
            doll.startPlaying(); PartyGuestGameTests.clean(helper);
            doll.cycleRemote(); PartyGuestGameTests.clean(helper);
            helper.destroyBlock(pos);
            for (Mob mob : mobs) mob.setDeltaMovement(Vec3.ZERO);
        }).thenIdle(5).thenExecute(() -> {
            for (Mob mob : mobs) helper.assertTrue(mob.getDeltaMovement().horizontalDistanceSqr() < 1e-8, "Removed dolls apply no orbit force");
            ServerConfig.ORBIT_SPEED.set(originalSpeed.doubleValue());
            helper.getLevel().getServer().getPlayerList().remove(player);
        }).thenSucceed();
    }

    private static void remoteClick(ServerPlayer player, ItemStack stack) {
        player.stopUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        FriendsWine.REMOTE.get().use(player.level(), player, InteractionHand.MAIN_HAND);
        for (var guest:player.level().getEntitiesOfClass(PartyGuestEntity.class,player.getBoundingBox().inflate(50))) guest.discard();
    }

    private static void bind(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
        player.stopUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = helper.absolutePos(DOLL_POS);
        helper.getBlockState(DOLL_POS).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static Pig pig(GameTestHelper helper, Vec3 position, boolean disableWalking) {
        Pig pig = helper.spawn(EntityType.PIG, position);
        // Keep vanilla navigation and collision active; remove only random animal goals.
        pig.removeFreeWill();
        pig.setOnGround(true);
        if (disableWalking) {
            pig.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        }
        return pig;
    }

    private static void click(GameTestHelper helper, ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos pos = helper.absolutePos(DOLL_POS);
        helper.assertTrue(helper.getBlockState(DOLL_POS).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)).consumesAction(),
                "The registered block must consume the right-click interaction");
        PartyGuestGameTests.clean(helper);
    }

    private static void clickWithItem(GameTestHelper helper, ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        BlockPos pos = helper.absolutePos(DOLL_POS);
        helper.assertTrue(helper.getBlockState(DOLL_POS).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)).consumesAction(),
                "The registered block must consume the held-item right-click interaction");
        PartyGuestGameTests.clean(helper);
    }

    private static void assertHorizontalStill(GameTestHelper helper, Pig pig, Vec3 start, String message) {
        double dx = pig.getX() - start.x;
        double dz = pig.getZ() - start.z;
        helper.assertTrue(dx * dx + dz * dz < 1.0e-8 && pig.getDeltaMovement().horizontalDistanceSqr() < 1.0e-8, message);
    }
}
