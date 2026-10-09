package com.friendswine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class PeaceGameTests {
    public PeaceGameTests() {}

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_range", batch = "friendswine-peace", timeoutTicks = 130)
    public static void protectionAndRelease(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        BlockPos pos = new BlockPos(54, 2, 54);
        helper.setBlock(pos, FriendsWine.DOLL.get());
        DollBlockEntity doll = helper.getBlockEntity(pos);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(pos));
        Mob attacker = stationary(helper, EntityType.ZOMBIE, center.add(2, 0, 0));
        Mob outside = stationary(helper, EntityType.SKELETON, center.add(51, 0, 0));
        Mob brainMob = stationary(helper, EntityType.PIGLIN, center.add(4, 0, 0));
        Pig victim = stationary(helper, EntityType.PIG, center.add(3, 0, 0));
        Pig outsideVictim = stationary(helper, EntityType.PIG, center.add(52, 0, 0));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(center.add(6, 0, 0));
        // ServerPlayer has 60 ticks of spawn immunity, including the native GameTest mock.
        helper.startSequence().thenIdle(65).thenExecute(() -> {
            checkAllowed(helper, player, player.damageSources().mobAttack(outside));
            attacker.setTarget(victim);
            outside.setTarget(player);
            brainMob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, victim);
            helper.assertTrue(attacker.getTarget() == victim && outside.getTarget() == player
                            && brainMob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),
                    "Existing targets must be present before activation");
            doll.cycleRemote(); PartyGuestGameTests.clean(helper);
            attacker.setTarget(player);
            helper.assertTrue(attacker.getTarget() != player, "Squash-only dolls must block new mob-to-player targets");
            checkBlocked(helper, victim, victim.damageSources().mobAttack(attacker));
            checkBlocked(helper, player, player.damageSources().mobAttack(outside));
            checkBlocked(helper, outsideVictim, outsideVictim.damageSources().mobAttack(attacker));
            var arrow = helper.spawn(EntityType.ARROW, new Vec3(100, 4, 54));
            arrow.setOwner(outside);
            checkBlocked(helper, victim, victim.damageSources().arrow(arrow, outside));
            arrow.discard();
            checkAllowed(helper, outsideVictim, outsideVictim.damageSources().mobAttack(outside));
            checkAllowed(helper, attacker, attacker.damageSources().playerAttack(player));
            checkAllowed(helper, victim, victim.damageSources().fall());
            victim.moveTo(center.add(50, 0, 0));
            checkBlocked(helper, victim, victim.damageSources().mobAttack(outside));
            victim.moveTo(center.add(50.01, 0, 0));
            checkAllowed(helper, victim, victim.damageSources().mobAttack(outside));
            victim.moveTo(center.add(3, 0, 0));
        }).thenIdle(2).thenExecute(() -> {
            helper.assertTrue(attacker.getTarget() == null && outside.getTarget() == null
                            && !brainMob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),
                    "Activation must clear existing nearby/outside targets and registered Brain attack memory");
            doll.cycleRemote(); PartyGuestGameTests.clean(helper);
            checkBlocked(helper, victim, victim.damageSources().mobAttack(outside));
            doll.setRemoved();
            helper.assertTrue(DollBlockEntity.nearestActive(helper.getLevel(), center, 50) == null,
                    "Unloading must release the protection immediately");
            checkAllowed(helper, victim, victim.damageSources().mobAttack(outside));
            long start = doll.getStartTick();
            doll.clearRemoved();
            helper.assertTrue(doll.isPlaying() && doll.getStartTick() == start,
                    "Reloading must resume the saved active mode and timeline");
            checkBlocked(helper, victim, victim.damageSources().mobAttack(outside));
            doll.stopPlaying();
            attacker.setTarget(victim);
            helper.assertTrue(attacker.getTarget() == victim, "Stopping must permit normal targeting again");
            checkAllowed(helper, victim, victim.damageSources().mobAttack(outside));
            doll.startPlaying(); PartyGuestGameTests.clean(helper);
            helper.destroyBlock(pos);
            checkAllowed(helper, victim, victim.damageSources().mobAttack(outside));
            helper.getLevel().getServer().getPlayerList().remove(player);
        }).thenSucceed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_range", batch = "friendswine-creeper", timeoutTicks = 180)
    public static void creeperStillExplodes(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        BlockPos pos = new BlockPos(4, 1, 12);
        helper.setBlock(pos, FriendsWine.DOLL.get());
        DollBlockEntity doll = helper.getBlockEntity(pos);
        Vec3 location = helper.absoluteVec(new Vec3(30.5, 1, 12.5));
        for (BlockPos floor : BlockPos.betweenClosed(26, 0, 8, 48, 0, 16)) helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(new BlockPos(30, 0, 12), Blocks.GRAY_WOOL);
        // The server mock hard-codes isCreative() to true. Use the native survival Player mock for explosions.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.getLevel().addFreshEntity(player);
        player.setNoGravity(true);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        player.setHealth(200);
        player.moveTo(location.add(2, 0, 0));
        Creeper natural = helper.spawn(EntityType.CREEPER, new Vec3(30.5, 1, 12.5));
        natural.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Pig victim = stationary(helper, EntityType.PIG, helper.absoluteVec(new Vec3(42.5, 1, 12.5)));
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        victim.setHealth(200);
        Creeper ignited = helper.spawn(EntityType.CREEPER, new Vec3(44.5, 1, 12.5));
        ignited.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        boolean griefing = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        helper.startSequence().thenIdle(65).thenExecute(() -> {
            checkAllowed(helper, player, player.damageSources().explosion(natural, natural));
            player.setHealth(200);
            player.invulnerableTime = 0;
            player.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, helper.getLevel().getServer());
            doll.startPlaying(); PartyGuestGameTests.clean(helper);
            natural.setTarget(player);
            helper.assertTrue(natural.getTarget() == player, "Protected creepers must retain their native fuse target");
        }).thenIdle(5).thenExecute(() -> {
            helper.assertTrue(natural.getSwellDir() > 0 && !natural.isIgnited(),
                    "A nearby target must still trigger the natural swelling goal");
        }).thenWaitUntil(() -> helper.assertTrue(natural.isRemoved(), "The native fuse must actually explode"))
                .thenExecute(() -> {
                    helper.assertTrue(player.getHealth() < 200, "Creeper explosions must still damage protected players: health="
                            + player.getHealth() + ", distance=" + player.position().distanceTo(natural.position()));
                    helper.assertTrue(helper.getBlockState(new BlockPos(30, 0, 12)).isAir(),
                            "Mob griefing enabled must allow native explosion block damage");
                    helper.setBlock(new BlockPos(44, 0, 12), Blocks.GRAY_WOOL);
                    helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, helper.getLevel().getServer());
                    ignited.ignite();
                }).thenWaitUntil(() -> helper.assertTrue(ignited.isRemoved(), "Manual ignition must still explode"))
                .thenExecute(() -> {
                    helper.assertTrue(victim.getHealth() < 200, "Creeper explosions must still damage protected mobs");
                    helper.assertTrue(helper.getBlockState(new BlockPos(44, 0, 12)).is(Blocks.GRAY_WOOL),
                            "Mob griefing disabled must preserve blocks");
                    helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, helper.getLevel().getServer());
                    doll.stopPlaying();
                    player.discard();
                }).thenSucceed();
    }

    private static <T extends Mob> T stationary(GameTestHelper helper, EntityType<T> type, Vec3 absolute) {
        T mob = type.create(helper.getLevel());
        mob.moveTo(absolute);
        mob.setNoGravity(true);
        mob.setNoAi(true);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void checkBlocked(GameTestHelper helper, LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        victim.invulnerableTime = 0;
        float health = victim.getHealth();
        helper.assertFalse(victim.hurt(source, 2), "Protected mob combat must cancel native damage");
        helper.assertTrue(victim.getHealth() == health, "Blocked damage must not change health");
    }

    private static void checkAllowed(GameTestHelper helper, LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        victim.invulnerableTime = 0;
        float health = victim.getHealth();
        helper.assertTrue(victim.hurt(source, 2) && victim.getHealth() < health, "Unprotected damage must remain native");
    }
}
