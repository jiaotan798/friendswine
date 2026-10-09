package com.friendswine;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Registered spawn eggs, real navigation, item ownership and activation-triggered spawning. */
public final class PartyGuestGameTests {
    private static final BlockPos DOLL=new BlockPos(12,1,12);
    public PartyGuestGameTests() {}

    /** Existing gameplay tests remove incidental guests; these cases run in their own batches. */
    public static void clean(GameTestHelper helper) {
        cleanExcept(helper);
    }
    private static void cleanExcept(GameTestHelper helper,PartyGuestEntity... retained) {
        List<PartyGuestEntity> keep=List.of(retained);
        for (var guest:guests(helper)) if (!keep.contains(guest)) guest.discard();
    }
    private static List<PartyGuestEntity> guests(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(PartyGuestEntity.class,
                new AABB(helper.absolutePos(new BlockPos(12,1,12))).inflate(50),guest->guest.isAlive());
    }
    private static void prepare(GameTestHelper helper) {
        clean(helper);
        for (BlockPos pos:BlockPos.betweenClosed(0,1,0,31,4,23)) helper.setBlock(pos,Blocks.AIR);
        for (BlockPos pos:BlockPos.betweenClosed(0,0,0,31,0,23)) helper.setBlock(pos,Blocks.STONE);
    }
    private static PartyGuestEntity guest(GameTestHelper helper,boolean emma,Vec3 relative,boolean noAi) {
        var entity=(emma ? FriendsWine.EMMA.get() : FriendsWine.KASUMI.get()).create(helper.getLevel(),EntitySpawnReason.LOAD);
        Vec3 pos=helper.absoluteVec(relative);
        entity.snapTo(pos.x,pos.y,pos.z,0,0); entity.setNoAi(noAi); entity.setOnGround(true);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
    private static ServerPlayer player(GameTestHelper helper,Vec3 relative) {
        ServerPlayer player=WineGameTests.mockServerPlayer(helper);
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        player.snapTo(helper.absoluteVec(relative)); return player;
    }
    private static boolean griefing(GameTestHelper helper) {
        return helper.getLevel().getGameRules().get(GameRules.MOB_GRIEFING);
    }
    private static void griefing(GameTestHelper helper,boolean enabled) {
        helper.getLevel().getGameRules().set(GameRules.MOB_GRIEFING,enabled,helper.getLevel().getServer());
    }
    private static void remove(GameTestHelper helper,ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure="friendswine:doll_range",environment="friendswine-tests:eggsandnaturalconditions",maxTicks=100)
    public void eggsAndNaturalConditions(GameTestHelper helper) {
        prepare(helper);
        ServerPlayer player=player(helper,new Vec3(6.5,1,8.5));
        Item[] eggs={FriendsWine.KASUMI_SPAWN_EGG.get(),FriendsWine.EMMA_SPAWN_EGG.get()};
        for (int i=0;i<eggs.length;i++) {
            BlockPos relative=new BlockPos(6+i*5,0,6),absolute=helper.absolutePos(relative);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(eggs[i]));
            var result=eggs[i].useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false)));
            helper.assertTrue(result.consumesAction(),"Registered egg must perform an actual use-on spawn");
            boolean emma=i==1;
            helper.assertTrue(guests(helper).stream().filter(e->e.isEmma()==emma).count()==1,"Each egg must create its own registered type");
            helper.assertTrue(player.getMainHandItem().isEmpty(),"Survival spawn egg is consumed once");
        }
        for (var guest:guests(helper)) {
            helper.assertTrue(Math.abs(guest.getBbWidth()-0.6F)<0.001 && Math.abs(guest.getBbHeight()-1F)<0.001,"Both fixed hitboxes are 0.6 by 1");
            helper.assertTrue(guest.getMaxHealth()==10,"Both guests have ten health");
        }
        long originalTime=helper.getLevel().getOverworldClockTime();
        helper.getLevel().clockManager().setTotalTicks(helper.getLevel().dimensionType().defaultClock().orElseThrow(),1000);
        BlockPos pos=new BlockPos(20,1,5),absolute=helper.absolutePos(pos);
        helper.setBlock(pos.below(),Blocks.GRASS_BLOCK);
        for (var type:List.of(FriendsWine.KASUMI.get(),FriendsWine.EMMA.get())) {
            helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.getPlacementType(type)==net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,"Both registered types use on-ground spawn placement");
            helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type,helper.getLevel(),EntitySpawnReason.NATURAL,absolute,RandomSource.create(1)),"Registered natural spawn entry point accepts dry animal ground");
            helper.assertTrue(PartyGuestEntity.checkNaturalSpawn(type,helper.getLevel(),EntitySpawnReason.NATURAL,absolute,RandomSource.create(1)),"Daylit dry animal-spawnable land permits rare natural spawning");
            helper.setBlock(pos.below(),Blocks.STONE);
            helper.assertFalse(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type,helper.getLevel(),EntitySpawnReason.NATURAL,absolute,RandomSource.create(1)),"Registered natural spawn entry point rejects stone ground");
            helper.assertFalse(PartyGuestEntity.checkNaturalSpawn(type,helper.getLevel(),EntitySpawnReason.NATURAL,absolute,RandomSource.create(1)),"Stone is not animal-spawnable ground");
            helper.setBlock(pos.below(),Blocks.GRASS_BLOCK); helper.setBlock(pos,Blocks.WATER);
            helper.assertFalse(PartyGuestEntity.checkNaturalSpawn(type,helper.getLevel(),EntitySpawnReason.NATURAL,absolute,RandomSource.create(1)),"Water rejects natural spawning");
            helper.setBlock(pos,Blocks.AIR);
            ServerLevel nether=helper.getLevel().getServer().getLevel(Level.NETHER);
            if (nether!=null) helper.assertFalse(PartyGuestEntity.checkNaturalSpawn(type,nether,EntitySpawnReason.NATURAL,absolute,RandomSource.create(1)),"Natural spawning is Overworld only");
            ServerLevel end=helper.getLevel().getServer().getLevel(Level.END);
            if (end!=null) helper.assertFalse(PartyGuestEntity.checkNaturalSpawn(type,end,EntitySpawnReason.NATURAL,absolute,RandomSource.create(1)),"Natural spawning rejects the End");
        }
        helper.getLevel().clockManager().setTotalTicks(helper.getLevel().dimensionType().defaultClock().orElseThrow(),originalTime);
        remove(helper,player); clean(helper); helper.succeed();
    }

    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure="friendswine:doll_range",environment="friendswine-tests:stagespawnandcombinedcap",maxTicks=100)
    public void stageSpawnAndCombinedCap(GameTestHelper helper) {
        prepare(helper); helper.setBlock(DOLL,FriendsWine.DOLL.get());
        DollBlockEntity doll=helper.getBlockEntity(DOLL,DollBlockEntity.class);
        ServerPlayer player=player(helper,new Vec3(2.5,1,2.5));
        boolean seenKasumi=false,seenEmma=false,seenSix=false,seenTen=false;
        for (int seed=0;seed<32;seed++) {
            clean(helper); doll.stopPlaying(); helper.getLevel().getRandom().setSeed(seed);
            boolean remote=(seed&1)==1;
            if (remote) remoteFirstStage(helper,player); else clickDoll(helper,player);
            List<PartyGuestEntity> spawned=guests(helper);
            int count=spawned.size();
            helper.assertTrue(count>=6 && count<=10,"An actual closed-to-playing entry creates six to ten combined guests; seed="+seed+", count="+count);
            helper.assertTrue(doll.getMode()==(remote ? DollBlockEntity.SQUASH_ONLY : DollBlockEntity.FULL),"Right click starts stage two; the remote's first press starts stage one");
            seenKasumi|=spawned.stream().anyMatch(e->!e.isEmma());
            seenEmma|=spawned.stream().anyMatch(PartyGuestEntity::isEmma);
            seenSix|=count==6; seenTen|=count==10;
            for (PartyGuestEntity created:spawned) {
                created.setNoAi(true);
                helper.assertTrue(created.position().distanceToSqr(Vec3.atCenterOf(helper.absolutePos(DOLL)))<=256,"Activation guests spawn within sixteen blocks");
                helper.assertTrue(helper.getLevel().noCollision(created),"Activation uses safe non-colliding ground");
            }
            long song=doll.getStartTick();
            helper.assertFalse(doll.startPlaying(),"Starting an already-playing doll is rejected");
            helper.assertTrue(guests(helper).size()==count && doll.getStartTick()==song,"Repeated start neither spawns nor resets the song");
            if (remote) {
                doll.cycleRemote();
                helper.assertTrue(doll.getMode()==DollBlockEntity.FULL && guests(helper).size()==count,"Changing stage one to two does not spawn");
            }
            doll.cycleRemote();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && doll.getStartTick()==song && guests(helper).size()==count,"Changing stage two to three preserves the song and never spawns");
            CompoundTag saved=doll.getUpdateTag(helper.getLevel().registryAccess());
            doll.setRemoved();
            doll.loadAdditional(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,helper.getLevel().registryAccess(),saved));
            doll.clearRemoved();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && doll.getStartTick()==song,"Actual unload/load restores the saved stage and clock");
            helper.assertTrue(guests(helper).size()==count,"Loading a playing doll never repeats activation spawning");
            doll.cycleRemote();
            helper.assertTrue(!doll.isPlaying() && guests(helper).size()==count,"Fourth stage only stops playback");
        }
        helper.assertTrue(seenKasumi && seenEmma && seenSix && seenTen,"Fixed seeds cover both guest types and both six/ten spawn boundaries");

        // Real spawn-egg and NATURAL spawn entry points both contribute to the shared cap.
        clean(helper); doll.stopPlaying();
        BlockPos eggGround=helper.absolutePos(new BlockPos(4,0,8));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.KASUMI_SPAWN_EGG.get()));
        helper.assertTrue(FriendsWine.KASUMI_SPAWN_EGG.get().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(eggGround),Direction.UP,eggGround,false))).consumesAction(),"Preexisting egg guest is created through its registered item");
        PartyGuestEntity fromEgg=guests(helper).get(0); fromEgg.setNoAi(true);
        PartyGuestEntity natural=FriendsWine.EMMA.get().spawn(helper.getLevel(),helper.absolutePos(new BlockPos(6,1,8)),EntitySpawnReason.NATURAL);
        helper.assertTrue(natural!=null && natural.isEmma(),"A naturally initialized existing guest participates in the shared cap");
        natural.setNoAi(true);
        clickDoll(helper,player);
        helper.assertTrue(guests(helper).size()>=8 && guests(helper).size()<=10 && guests(helper).contains(fromEgg) && guests(helper).contains(natural),"Egg and natural individuals count before a six-to-ten activation attempt");
        int previous=guests(helper).size();
        for (var created:guests(helper)) created.setNoAi(true);
        doll.stopPlaying(); clickDoll(helper,player);
        helper.assertTrue(guests(helper).size()>=previous && guests(helper).size()<=10,"A new activation fills only remaining capacity");
        doll.stopPlaying(); clean(helper);

        for (int i=0;i<9;i++) guest(helper,(i&1)==1,new Vec3(4.5+i,1,5.5),true);
        clickDoll(helper,player);
        helper.assertTrue(guests(helper).size()==10,"Nine preexisting combined guests leave exactly one spawn slot");
        doll.stopPlaying(); clickDoll(helper,player);
        helper.assertTrue(guests(helper).size()==10,"Ten existing guests prevent further activation spawning");
        doll.stopPlaying(); clean(helper);

        for (BlockPos pos:BlockPos.betweenClosed(4,-2,4,20,5,20)) helper.setBlock(pos,Blocks.STONE);
        helper.setBlock(DOLL,FriendsWine.DOLL.get());
        doll=helper.getBlockEntity(DOLL,DollBlockEntity.class);
        clickDoll(helper,player);
        helper.assertTrue(doll.getMode()==DollBlockEntity.FULL && guests(helper).isEmpty(),"Blocked terrain still starts playback but never forces guests into solid blocks");
        doll.stopPlaying(); helper.destroyBlock(DOLL);
        for (BlockPos pos:BlockPos.betweenClosed(4,1,4,20,5,20)) helper.setBlock(pos,Blocks.AIR);
        clean(helper); remove(helper,player); helper.succeed();
    }

    private static void clickDoll(GameTestHelper helper,ServerPlayer player) {
        player.stopUsingItem(); player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        BlockPos absolute=helper.absolutePos(DOLL);
        helper.assertTrue(helper.getBlockState(DOLL).useWithoutItem(helper.getLevel(),player,new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false)).consumesAction(),"Registered ordinary right-click entry consumes its interaction");
    }

    private static void remoteFirstStage(GameTestHelper helper,ServerPlayer player) {
        player.stopUsingItem();
        ItemStack remote=new ItemStack(FriendsWine.REMOTE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,remote);
        BlockPos absolute=helper.absolutePos(DOLL);
        helper.getBlockState(DOLL).useItemOn(remote,helper.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false));
        helper.assertTrue(player.isUsingItem(),"Binding holds the remote use latch until the button is released");
        player.stopUsingItem();
        helper.assertFalse(player.isUsingItem(),"A separate remote press begins only after releasing the binding click");
        FriendsWine.REMOTE.get().use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
        player.stopUsingItem();
    }

    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure="friendswine:doll_range",environment="friendswine-tests:theftguardsandhands",maxTicks=100)
    public void theftGuardsAndHands(GameTestHelper helper) {
        prepare(helper);
        boolean originalGriefing=griefing(helper); griefing(helper,true);
        ServerPlayer player=player(helper,new Vec3(9.5,1,8.5));
        for (boolean emma:new boolean[]{false,true}) {
            PartyGuestEntity guest=guest(helper,emma,new Vec3(8.5,1,8.5),true);
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get(),3));
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get(),2));
            helper.assertTrue(guest.trySteal(player),"Adjacent visible survival holder can be robbed");
            helper.assertTrue(guest.getMainHandItem().getCount()==1 && player.getMainHandItem().getCount()==2 && player.getOffhandItem().getCount()==2,"Main hand has priority and exactly one doll changes ownership");
            helper.assertFalse(guest.trySteal(player),"Carrying a doll prevents another theft");
            guest.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            helper.assertTrue(guest.trySteal(player),"Off hand is used when main hand has no doll");
            helper.assertTrue(player.getOffhandItem().getCount()==1 && guest.getMainHandItem().getCount()==1,"Off-hand theft consumes exactly one");
            guest.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            for (GameType mode:new GameType[]{GameType.CREATIVE,GameType.SPECTATOR}) {
                player.setGameMode(mode);
                helper.assertTrue(mode==GameType.CREATIVE ? player.isCreative() && player.getAbilities().instabuild : player.isSpectator(),"The normal server player must actually enter the selected exempt mode");
                helper.assertFalse(guest.trySteal(player),"Creative and spectator holders are exempt");
            }
            player.setGameMode(GameType.ADVENTURE);
            helper.assertTrue(!player.isSpectator() && !player.getAbilities().instabuild,"Adventure must really retain non-creative abilities");
            helper.assertTrue(guest.trySteal(player),"Adventure holders have the same survival theft behavior");
            guest.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            griefing(helper,false); helper.assertFalse(guest.trySteal(player),"mobGriefing false forbids item theft");
            griefing(helper,true);
            player.snapTo(helper.absoluteVec(new Vec3(10.5,1,8.5)));
            helper.assertFalse(guest.trySteal(player),"Theft cannot occur beyond 1.5 blocks");
            player.snapTo(helper.absoluteVec(new Vec3(9.5,1,8.5)));
            helper.setBlock(new BlockPos(9,1,8),Blocks.STONE); helper.setBlock(new BlockPos(9,2,8),Blocks.STONE);
            helper.assertFalse(guest.trySteal(player),"A wall prevents line-of-sight theft");
            helper.setBlock(new BlockPos(9,1,8),Blocks.AIR); helper.setBlock(new BlockPos(9,2,8),Blocks.AIR);
            helper.setBlock(DOLL,FriendsWine.DOLL.get()); DollBlockEntity doll=(DollBlockEntity)helper.getBlockEntity(DOLL,DollBlockEntity.class); doll.startPlaying(); cleanExcept(helper,guest);
            helper.assertFalse(guest.trySteal(player),"An active nearby doll takes priority over a holder");
            helper.assertTrue(player.getMainHandItem().getCount()==1,"Failed guards never consume a player's item");
            doll.stopPlaying(); helper.destroyBlock(DOLL); guest.discard();
        }
        griefing(helper,originalGriefing); remove(helper,player); clean(helper); helper.succeed();
    }

    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure="friendswine:doll_range",environment="friendswine-tests:carrysaveplacementanddeath",maxTicks=100)
    public void carrySavePlacementAndDeath(GameTestHelper helper) {
        prepare(helper);
        boolean originalGriefing=griefing(helper); griefing(helper,true);
        boolean originalMobLoot=helper.getLevel().getGameRules().get(GameRules.MOB_DROPS);
        for (boolean emma:new boolean[]{false,true}) {
            PartyGuestEntity original=guest(helper,emma,new Vec3(12.5,1,12.5),true);
            ItemStack named=new ItemStack(FriendsWine.DOLL_ITEM.get()); named.set(DataComponents.CUSTOM_NAME,Component.literal("Guest carried doll"));
            original.setItemSlot(EquipmentSlot.MAINHAND,named);
            var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,helper.getLevel().registryAccess());
            original.saveWithoutId(output); CompoundTag saved=output.buildResult(); original.discard();
            PartyGuestEntity restored=(emma ? FriendsWine.EMMA.get() : FriendsWine.KASUMI.get()).create(helper.getLevel(),EntitySpawnReason.LOAD);
            restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,helper.getLevel().registryAccess(),saved)); restored.setUUID(UUID.randomUUID()); helper.getLevel().addFreshEntity(restored);
            helper.assertTrue(restored.isCarryingDoll() && restored.getMainHandItem().getCount()==1 && restored.getMainHandItem().getHoverName().getString().equals("Guest carried doll"),"Native equipment save/load preserves one doll and item metadata");
            helper.assertFalse(restored.removeWhenFarAway(100000),"A carrying guest cannot naturally despawn");
            griefing(helper,false);
            helper.assertFalse(restored.tryPlaceCarried(helper.getLevel()),"mobGriefing false forbids placement");
            helper.assertTrue(restored.isCarryingDoll(),"mobGriefing false preserves the carried doll");
            griefing(helper,true);
            for (BlockPos pos:BlockPos.betweenClosed(7,0,7,17,4,17)) helper.setBlock(pos,Blocks.STONE);
            helper.assertFalse(restored.tryPlaceCarried(helper.getLevel()),"No safe visible air block means placement fails");
            helper.assertTrue(restored.isCarryingDoll() && restored.getMainHandItem().getCount()==1,"Failed placement retains exactly one saved doll");
            for (BlockPos pos:BlockPos.betweenClosed(7,1,7,17,4,17)) helper.setBlock(pos,Blocks.AIR);
            for (BlockPos pos:BlockPos.betweenClosed(7,0,7,17,0,17)) helper.setBlock(pos,Blocks.MAGMA_BLOCK);
            helper.assertFalse(restored.tryPlaceCarried(helper.getLevel()),"Hazardous magma ground cannot receive a carried doll");
            helper.assertTrue(restored.isCarryingDoll(),"Hazard rejection preserves the original carried item");
            for (BlockPos pos:BlockPos.betweenClosed(7,0,7,17,0,17)) helper.setBlock(pos,Blocks.STONE);
            helper.assertTrue(restored.tryPlaceCarried(helper.getLevel()),"An available nearby safe floor permits real BlockItem placement");
            helper.assertTrue(!restored.isCarryingDoll() && restored.getMainHandItem().isEmpty(),"Successful placement consumes the carried item exactly once");
            DollBlockEntity doll=DollBlockEntity.nearestActive(helper.getLevel(),restored.position(),5);
            helper.assertTrue(doll!=null && doll.getMode()==DollBlockEntity.FULL,"The placed doll starts directly in stage two");
            helper.assertTrue(guests(helper).size()>=7 && guests(helper).size()<=10,"NPC placement activation creates six-to-nine guests while counting the carrier toward ten");
            cleanExcept(helper,restored);
            helper.assertTrue(doll.getBlockPos().distSqr(restored.blockPosition())<=16,"Placed doll remains within the four-block search");
            helper.assertFalse(restored.tryPlaceCarried(helper.getLevel()),"An empty hand cannot place another doll");
            doll.stopPlaying(); helper.getLevel().destroyBlock(doll.getBlockPos(),false);
            restored.setItemSlot(EquipmentSlot.MAINHAND,named.copy());
            for (ItemEntity item:helper.getLevel().getEntitiesOfClass(ItemEntity.class,restored.getBoundingBox().inflate(8))) item.discard();
            helper.getLevel().getGameRules().set(GameRules.MOB_DROPS,!emma,helper.getLevel().getServer());
            restored.die(restored.damageSources().generic());
            int drops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,restored.getBoundingBox().inflate(8)).stream()
                    .filter(item->item.getItem().is(FriendsWine.DOLL_ITEM.get())).mapToInt(item->item.getItem().getCount()).sum();
            helper.assertTrue(drops==1,"Death drops exactly one stolen doll, including when doMobLoot is disabled");
            helper.assertTrue(restored.getMainHandItem().isEmpty(),"Death clears carried ownership after the one drop");
            restored.die(restored.damageSources().generic());
            int repeatedDrops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,restored.getBoundingBox().inflate(8)).stream().filter(item->item.getItem().is(FriendsWine.DOLL_ITEM.get())).mapToInt(item->item.getItem().getCount()).sum();
            helper.assertTrue(repeatedDrops==1,"A repeated death notification cannot duplicate a carried doll");
            for (ItemEntity item:helper.getLevel().getEntitiesOfClass(ItemEntity.class,restored.getBoundingBox().inflate(8))) item.discard();
            restored.discard();
        }
        helper.getLevel().getGameRules().set(GameRules.MOB_DROPS,originalMobLoot,helper.getLevel().getServer());
        griefing(helper,originalGriefing); helper.succeed();
    }

    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure="friendswine:doll_range",environment="friendswine-tests:realaiholderandidledoll",maxTicks=300)
    public void realAiHolderAndIdleDoll(GameTestHelper helper) {
        prepare(helper);
        boolean originalGriefing=griefing(helper); griefing(helper,true);
        ServerPlayer holder=player(helper,new Vec3(3.5,1,12.5));
        holder.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
        BlockPos idlePos=new BlockPos(20,1,12);
        helper.setBlock(idlePos,FriendsWine.DOLL.get());
        PartyGuestEntity guest=guest(helper,false,new Vec3(7.5,1,12.5),false);
        Vec3 start=guest.position();
        int[] activationPopulation={0};
        helper.onEachTick(()->{
            activationPopulation[0]=Math.max(activationPopulation[0],guests(helper).size());
            cleanExcept(helper,guest);
        });
        helper.startSequence().thenIdle(12).thenExecute(()->{
            helper.assertTrue(guest.getX()<start.x-0.1,"Normal AI physically approaches the holder instead of the idle doll");
        }).thenIdle(78).thenExecute(()->{
            helper.assertTrue(holder.getMainHandItem().isEmpty(),"Normal AI reaches and steals the holder's doll");
            DollBlockEntity placed=DollBlockEntity.nearestActive(helper.getLevel(),guest.position(),20);
            helper.assertTrue(placed!=null && placed.getMode()==DollBlockEntity.FULL,"Normal AI places the stolen doll and starts stage two");
            helper.assertTrue(activationPopulation[0]>=7 && activationPopulation[0]<=10,"Actual NPC AI placement uses activation spawning and the combined cap");
            cleanExcept(helper,guest);
            placed.stopPlaying(); helper.getLevel().destroyBlock(placed.getBlockPos(),false);
            guest.snapTo(helper.absoluteVec(new Vec3(7.5,1,12.5))); guest.setDeltaMovement(Vec3.ZERO);
        }).thenIdle(30).thenExecute(()->{
            helper.assertTrue(guest.getX()>start.x+0.1,"With no holder or active doll, normal AI approaches the loaded idle doll");
            guest.snapTo(helper.absoluteVec(new Vec3(4.5,1,12.5))); guest.setDeltaMovement(Vec3.ZERO);
            holder.snapTo(helper.absoluteVec(new Vec3(3.5,1,12.5)));
            holder.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            DollBlockEntity idle=(DollBlockEntity)helper.getBlockEntity(idlePos,DollBlockEntity.class); idle.startPlaying(); cleanExcept(helper,guest);
        }).thenIdle(30).thenExecute(()->{
            helper.assertTrue(holder.getMainHandItem().getCount()==1,"Actual active-doll AI priority leaves the adjacent holder's item untouched");
            helper.assertTrue(guest.getX()>helper.absoluteVec(new Vec3(4.5,1,12.5)).x+0.1,"Active doll attraction moves the guest toward the doll");
            ((DollBlockEntity)helper.getBlockEntity(idlePos,DollBlockEntity.class)).stopPlaying();
            clean(helper); remove(helper,holder); griefing(helper,originalGriefing);
        }).thenSucceed();
    }
}
