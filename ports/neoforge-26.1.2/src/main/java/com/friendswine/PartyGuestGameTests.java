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

/** 验证生成蛋、寻路、物品归属，以及第三档生成和三种生物共用的数量上限。 */
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
    private static EntityType<PartyGuestEntity> type(GuestVariant variant) {
        return switch (variant) { case KASUMI -> FriendsWine.KASUMI.get(); case EMMA -> FriendsWine.EMMA.get(); case AMIYA -> FriendsWine.AMIYA.get(); };
    }
    private static PartyGuestEntity guest(GameTestHelper helper,GuestVariant variant,Vec3 relative,boolean noAi) {
        var entity=type(variant).create(helper.getLevel(),EntitySpawnReason.LOAD);
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
    /** 截获原版发给玩家的声音数据包，同时保留真实连接，验证被偷者和旁观者实际收到广播。 */
    private static ServerPlayer soundPlayer(GameTestHelper helper, Vec3 relative, List<net.minecraft.network.protocol.game.ClientboundSoundPacket> sounds) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TheftSoundTest"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        channel.pipeline().addLast(new io.netty.channel.ChannelOutboundHandlerAdapter() {
            @Override public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) throws Exception {
                if (message instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound && sound.getSound().value() == FriendsWine.GUEST_STEAL.get()) sounds.add(sound);
                super.write(context, message, promise);
            }
        });
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

    public static void eggsAndNaturalConditions(GameTestHelper helper) {
        prepare(helper);
        ServerPlayer player=player(helper,new Vec3(6.5,1,8.5));
        Item[] eggs={FriendsWine.KASUMI_SPAWN_EGG.get(),FriendsWine.EMMA_SPAWN_EGG.get(),FriendsWine.AMIYA_SPAWN_EGG.get()};
        for (int i=0;i<eggs.length;i++) {
            BlockPos relative=new BlockPos(6+i*5,0,6),absolute=helper.absolutePos(relative);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(eggs[i]));
            var result=eggs[i].useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false)));
            helper.assertTrue(result.consumesAction(),"Registered egg must perform an actual use-on spawn");
            GuestVariant variant=GuestVariant.values()[i];
            helper.assertTrue(guests(helper).stream().filter(e->e.variant()==variant).count()==1,"Each egg must create its own registered type");
            helper.assertTrue(player.getMainHandItem().isEmpty(),"Survival spawn egg is consumed once");
        }
        for (var guest:guests(helper)) {
            helper.assertTrue(Math.abs(guest.getBbWidth()-0.6F)<0.001 && Math.abs(guest.getBbHeight()-1F)<0.001,"All three fixed hitboxes are 0.6 by 1");
            helper.assertTrue(guest.getMaxHealth()==10,"All three guests have ten health");
        }
        long originalTime=helper.getLevel().getOverworldClockTime();
        helper.getLevel().clockManager().setTotalTicks(helper.getLevel().dimensionType().defaultClock().orElseThrow(),1000);
        BlockPos pos=new BlockPos(20,1,5),absolute=helper.absolutePos(pos);
        helper.setBlock(pos.below(),Blocks.GRASS_BLOCK);
        for (var type:List.of(FriendsWine.KASUMI.get(),FriendsWine.EMMA.get(),FriendsWine.AMIYA.get())) {
            helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.getPlacementType(type)==net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,"All three registered types use on-ground spawn placement");
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

    /** 覆盖第三档生成、2／4 只边界、已有个体占位、重复切档及存档恢复。 */
    public static void stageSpawnAndCombinedCap(GameTestHelper helper) {
        prepare(helper); helper.setBlock(DOLL,FriendsWine.DOLL.get());
        DollBlockEntity doll=helper.getBlockEntity(DOLL,DollBlockEntity.class);
        ServerPlayer player=player(helper,new Vec3(2.5,1,2.5));
        boolean seenKasumi=false,seenEmma=false,seenAmiya=false,seenTwo=false,seenFour=false;
        for (int seed=0;seed<32;seed++) {
            clean(helper); doll.stopPlaying(); helper.getLevel().getRandom().setSeed(seed);
            boolean remote=(seed&1)==1;
            if (remote) remoteFirstStage(helper,player); else clickDoll(helper,player);
            helper.assertTrue(doll.getMode()==(remote ? DollBlockEntity.SQUASH_ONLY : DollBlockEntity.FULL),"Right click starts stage two; the remote's first press starts stage one");
            helper.assertTrue(guests(helper).isEmpty(),"Ordinary right click and remote stage one must never generate guests; seed="+seed);
            long song=doll.getStartTick();
            helper.assertFalse(doll.startPlaying(),"Starting an already-playing doll is rejected");
            helper.assertTrue(guests(helper).isEmpty() && doll.getStartTick()==song,"Repeated start neither spawns nor resets the song");
            if (remote) {
                doll.cycleRemote();
                helper.assertTrue(doll.getMode()==DollBlockEntity.FULL && guests(helper).isEmpty(),"Changing stage one to two does not spawn");
            }
            doll.cycleRemote();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && doll.getStartTick()==song,"Changing stage two to three preserves the song");
            List<PartyGuestEntity> spawned=guests(helper);
            int count=spawned.size();
            helper.assertTrue(count>=2 && count<=4,"Entering remote stage three creates two to four combined guests; seed="+seed+", count="+count);
            seenKasumi|=spawned.stream().anyMatch(e->e.variant()==GuestVariant.KASUMI);
            seenEmma|=spawned.stream().anyMatch(PartyGuestEntity::isEmma);
            seenAmiya|=spawned.stream().anyMatch(e->e.variant()==GuestVariant.AMIYA);
            seenTwo|=count==2; seenFour|=count==4;
            for (PartyGuestEntity created:spawned) {
                created.setNoAi(true);
                helper.assertTrue(created.position().distanceToSqr(Vec3.atCenterOf(helper.absolutePos(DOLL)))<=256,"Stage-three guests spawn within sixteen blocks");
                helper.assertTrue(helper.getLevel().noCollision(created),"Stage-three spawning uses safe non-colliding ground");
            }
            CompoundTag saved=doll.getUpdateTag(helper.getLevel().registryAccess());
            doll.setRemoved();
            doll.loadAdditional(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,helper.getLevel().registryAccess(),saved));
            doll.clearRemoved();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && doll.getStartTick()==song,"Actual unload/load restores the saved stage and clock");
            helper.assertTrue(guests(helper).size()==count,"Loading an orbiting doll never repeats stage-three spawning");
            doll.cycleRemote();
            helper.assertTrue(!doll.isPlaying() && guests(helper).size()==count,"Fourth stage only stops playback");
        }
        helper.assertTrue(seenKasumi && seenEmma && seenAmiya && seenTwo && seenFour,"Fixed seeds cover all three guest types and both two/four spawn boundaries");

        // 通过真实生成蛋和自然生成入口验证已有个体共同占用上限。
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
        helper.assertTrue(guests(helper).size()==2,"Stage two leaves the preexisting egg and natural guests unchanged");
        doll.cycleRemote();
        helper.assertTrue(guests(helper).size()==4 && guests(helper).contains(fromEgg) && guests(helper).contains(natural),"Egg and natural individuals count toward the stage-three combined cap of four");
        for (var created:guests(helper)) created.setNoAi(true);
        for (int repeat=0;repeat<3;repeat++) {
            doll.stopPlaying(); clickDoll(helper,player);
            helper.assertTrue(guests(helper).size()==4,"Restarting stage two never adds guests");
            doll.cycleRemote();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && guests(helper).size()==4,"Repeated stage-three entry cannot exceed the combined cap of four");
        }
        doll.stopPlaying(); clean(helper);

        for (int i=0;i<3;i++) guest(helper,GuestVariant.values()[i%3],new Vec3(4.5+i,1,5.5),true);
        clickDoll(helper,player);
        helper.assertTrue(guests(helper).size()==3,"Stage two leaves three preexisting guests unchanged");
        doll.cycleRemote();
        helper.assertTrue(guests(helper).size()==4,"Three preexisting combined guests leave exactly one stage-three spawn slot");
        doll.stopPlaying(); clickDoll(helper,player);
        doll.cycleRemote();
        helper.assertTrue(guests(helper).size()==4,"Four existing guests prevent further stage-three spawning");
        doll.stopPlaying(); clean(helper);

        for (BlockPos pos:BlockPos.betweenClosed(4,-2,4,20,5,20)) helper.setBlock(pos,Blocks.STONE);
        helper.setBlock(DOLL,FriendsWine.DOLL.get());
        doll=helper.getBlockEntity(DOLL,DollBlockEntity.class);
        clickDoll(helper,player);
        helper.assertTrue(doll.getMode()==DollBlockEntity.FULL && guests(helper).isEmpty(),"Blocked terrain still starts playback but never forces guests into solid blocks");
        doll.cycleRemote();
        helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && guests(helper).isEmpty(),"Blocked terrain still enters stage three but never forces guests into solid blocks");
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

    public static void theftGuardsAndHands(GameTestHelper helper) {
        prepare(helper);
        boolean originalGriefing=griefing(helper); griefing(helper,true);
        var victimSounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
        var nearbySounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
        var farSounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
        ServerPlayer player=soundPlayer(helper,new Vec3(9.5,1,8.5),victimSounds);
        ServerPlayer nearby=soundPlayer(helper,new Vec3(12.5,1,8.5),nearbySounds);
        ServerPlayer far=soundPlayer(helper,new Vec3(30.5,1,8.5),farSounds);
        int successes=0;
        for (GuestVariant variant:GuestVariant.values()) {
            PartyGuestEntity guest=guest(helper,variant,new Vec3(8.5,1,8.5),true);
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get(),3));
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get(),2));
            helper.assertTrue(guest.trySteal(player),"Adjacent visible survival holder can be robbed");
            successes++;
            helper.assertTrue(victimSounds.size()==successes && nearbySounds.size()==successes && farSounds.isEmpty(),"三角色成功偷取只向被偷者及附近玩家播放一次");
            var sound=victimSounds.get(successes-1);
            helper.assertTrue(sound.getSource()==net.minecraft.sounds.SoundSource.NEUTRAL && sound.getVolume()==1F && sound.getPitch()==1F,"偷取音效沿用友好生物音量分类及原始音高");
            // 原版声包先量化为 1/8 格，再以 float 还原；大坐标测试世界会额外舍入，按协议核对来源。
            helper.assertTrue(sound.getX()==(int)(guest.getX()*8)/8.0F && sound.getY()==(int)(guest.getY()*8)/8.0F && sound.getZ()==(int)(guest.getZ()*8)/8.0F,"声音来源为成功偷取的生物位置");
            helper.assertTrue(guest.getMainHandItem().getCount()==1 && player.getMainHandItem().getCount()==2 && player.getOffhandItem().getCount()==2,"Main hand has priority and exactly one doll changes ownership");
            helper.assertFalse(guest.trySteal(player),"Carrying a doll prevents another theft");
            helper.assertTrue(victimSounds.size()==successes && nearbySounds.size()==successes,"继续携带时不重复播放");
            guest.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            helper.assertTrue(guest.trySteal(player),"Off hand is used when main hand has no doll");
            successes++;
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
            successes++;
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
            helper.assertTrue(victimSounds.size()==successes && nearbySounds.size()==successes && farSounds.isEmpty(),"副手和冒险模式成功时各播放一次，全部失败分支不播放且远处玩家收不到");
            doll.stopPlaying(); helper.destroyBlock(DOLL); guest.discard();
        }
        griefing(helper,originalGriefing); remove(helper,player); remove(helper,nearby); remove(helper,far); clean(helper); helper.succeed();
    }

    public static void carrySavePlacementAndDeath(GameTestHelper helper) {
        prepare(helper);
        boolean originalGriefing=griefing(helper); griefing(helper,true);
        boolean originalMobLoot=helper.getLevel().getGameRules().get(GameRules.MOB_DROPS);
        for (GuestVariant variant:GuestVariant.values()) {
            PartyGuestEntity original=guest(helper,variant,new Vec3(12.5,1,12.5),true);
            ItemStack named=new ItemStack(FriendsWine.DOLL_ITEM.get()); named.set(DataComponents.CUSTOM_NAME,Component.literal("Guest carried doll"));
            original.setItemSlot(EquipmentSlot.MAINHAND,named);
            var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,helper.getLevel().registryAccess());
            original.saveWithoutId(output); CompoundTag saved=output.buildResult(); original.discard();
            PartyGuestEntity restored=type(variant).create(helper.getLevel(),EntitySpawnReason.LOAD);
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
            helper.assertTrue(guests(helper).size()==1 && guests(helper).contains(restored),"NPC stage-two placement must never generate additional guests");
            doll.cycleRemote();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && guests(helper).size()>=3 && guests(helper).size()<=4 && guests(helper).contains(restored),"The placed doll generates two to three additional guests only on stage-three entry while counting its carrier toward four");
            cleanExcept(helper,restored);
            helper.assertTrue(doll.getBlockPos().distSqr(restored.blockPosition())<=16,"Placed doll remains within the four-block search");
            helper.assertFalse(restored.tryPlaceCarried(helper.getLevel()),"An empty hand cannot place another doll");
            doll.stopPlaying(); helper.getLevel().destroyBlock(doll.getBlockPos(),false);
            restored.setItemSlot(EquipmentSlot.MAINHAND,named.copy());
            for (ItemEntity item:helper.getLevel().getEntitiesOfClass(ItemEntity.class,restored.getBoundingBox().inflate(8))) item.discard();
            helper.getLevel().getGameRules().set(GameRules.MOB_DROPS,variant!=GuestVariant.EMMA,helper.getLevel().getServer());
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

    public static void realAiHolderAndIdleDoll(GameTestHelper helper) {
        prepare(helper);
        boolean originalGriefing=griefing(helper); griefing(helper,true);
        ServerPlayer holder=player(helper,new Vec3(3.5,1,12.5));
        holder.getInventory().setItem(35,new ItemStack(FriendsWine.DOLL_ITEM.get()));
        BlockPos idlePos=new BlockPos(20,1,12);
        helper.setBlock(idlePos,FriendsWine.DOLL.get());
        PartyGuestEntity guest=guest(helper,GuestVariant.KASUMI,new Vec3(7.5,1,12.5),false);
        Vec3 start=guest.position();
        int[] activationPopulation={0};
        helper.onEachTick(()->{
            activationPopulation[0]=Math.max(activationPopulation[0],guests(helper).size());
            cleanExcept(helper,guest);
        });
        helper.startSequence().thenIdle(12).thenExecute(()->{
            helper.assertTrue(guest.getX()<start.x-0.1,"Normal AI physically follows the backpack doll holder instead of the idle doll");
            helper.assertTrue(holder.getInventory().getItem(35).getCount()==1 && !guest.isCarryingDoll(),"实际跟随期间不会偷取背包物品");
            holder.setItemInHand(InteractionHand.MAIN_HAND,holder.getInventory().getItem(35));
            holder.getInventory().setItem(35,ItemStack.EMPTY);
        }).thenIdle(78).thenExecute(()->{
            helper.assertTrue(holder.getMainHandItem().isEmpty(),"Normal AI reaches and steals the holder's doll");
            DollBlockEntity placed=DollBlockEntity.nearestActive(helper.getLevel(),guest.position(),20);
            helper.assertTrue(placed!=null && placed.getMode()==DollBlockEntity.FULL,"Normal AI places the stolen doll and starts stage two");
            helper.assertTrue(activationPopulation[0]==1,"Actual NPC AI stage-two placement never creates additional guests");
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

    /** 验证拒绝常规伤害和击退，保留虚空／清理并只返还一件装备。 */
    public static void immunityAndCleanup(GameTestHelper helper) {
        prepare(helper);
        ServerPlayer attacker=player(helper,new Vec3(3.5,1,8.5));
        boolean originalMobLoot=helper.getLevel().getGameRules().get(GameRules.MOB_DROPS);
        helper.getLevel().getGameRules().set(GameRules.MOB_DROPS,false,helper.getLevel().getServer());
        for (GuestVariant variant:GuestVariant.values()) {
            PartyGuestEntity guest=guest(helper,variant,new Vec3(8.5,1,8.5),true);
            guest.setNoGravity(true);
            float health=guest.getHealth();
            var sources=guest.damageSources();
            for (var source:List.of(sources.playerAttack(attacker),sources.mobAttack(attacker),sources.arrow(null,attacker),
                    sources.explosion(attacker,attacker),sources.fall(),sources.inFire(),sources.lava(),sources.drown(),sources.generic())) {
                guest.invulnerableTime=0;
                helper.assertFalse(guest.hurtServer(helper.getLevel(), source, 1),"常规伤害必须被拒绝："+variant);
                helper.assertTrue(guest.getHealth()==health,"无敌不能靠受伤后回血实现");
            }
            guest.setDeltaMovement(Vec3.ZERO);
            attacker.attack(guest);
            guest.knockback(1,1,0);
            helper.getLevel().explode(attacker,guest.getX(),guest.getY(),guest.getZ(),2,Level.ExplosionInteraction.NONE);
            helper.assertTrue(guest.getHealth()==health && guest.getDeltaMovement().lengthSqr()<1e-9,"近战、横扫与真实爆炸都不能造成伤害或击退");
            var source=sources.fellOutOfWorld();
            guest.invulnerableTime=0;
            helper.assertTrue(guest.hurtServer(helper.getLevel(), source, 1) && guest.getHealth()<health,"虚空伤害保留清理能力");
            guest.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.kill(helper.getLevel());
            helper.assertTrue(!guest.isAlive() && guest.getMainHandItem().isEmpty(),"真实 /kill 入口可清理并释放携带物");
            int drops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,guest.getBoundingBox().inflate(3)).stream()
                    .filter(item->item.getItem().is(FriendsWine.DOLL_ITEM.get())).mapToInt(item->item.getItem().getCount()).sum();
            helper.assertTrue(drops==1,"关闭生物掉落时 /kill 也只返还一个携带玩偶");
            for (var item:helper.getLevel().getEntitiesOfClass(ItemEntity.class,guest.getBoundingBox().inflate(3))) item.discard();
            guest.discard();
        }
        helper.getLevel().getGameRules().set(GameRules.MOB_DROPS,originalMobLoot,helper.getLevel().getServer());
        remove(helper,attacker); clean(helper); helper.succeed();
    }

    /** 验证三角色的背包／双手跟随、就近切换、等距保持与远距离兜底，以及跟随与偷取条件的分离。 */
    public static void theftSpeedAndRestoration(GameTestHelper helper) {
        prepare(helper);
        boolean originalGriefing=griefing(helper); griefing(helper,true);
        ServerPlayer holder=player(helper,new Vec3(3.5,1,12.5));
        ServerPlayer other=player(helper,new Vec3(18.5,1,12.5));
        BlockPos idlePos=new BlockPos(24,1,12);
        helper.setBlock(idlePos,FriendsWine.DOLL.get());
        for (GuestVariant variant:GuestVariant.values()) {
            holder.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            PartyGuestEntity guest=guest(helper,variant,new Vec3(12.5,1,12.5),false);
            guest.tickCount=10;
            // 热键栏与背包首尾槽均只用于跟随，物品有自定义名称也不能漏判。
            for (int slot:new int[]{8,9,35}) {
                ItemStack doll=new ItemStack(FriendsWine.DOLL_ITEM.get());
                doll.set(DataComponents.CUSTOM_NAME,Component.literal("背包玩偶"));
                holder.getInventory().setItem(slot,doll);
                guest.customServerAiStep(helper.getLevel());
                guest.getNavigation().tick();
                helper.assertFalse(guest.getNavigation().isDone(),"背包玩偶能建立真实跟随路径："+variant+"/slot="+slot);
                helper.assertTrue(guest.getMoveControl().getSpeedModifier()==2,"背包跟随也使用 2 倍速度");
                helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(holder.blockPosition()),"导航目标为背包持有者");
                helper.assertFalse(guest.removeWhenFarAway(256*256),"跟随期间免于远距离自然消失");
                helper.assertTrue(guest.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue()==0.25,"基础速度保持 0.25");
                guest.snapTo(helper.absoluteVec(new Vec3(4.5,1,12.5)));
                helper.assertFalse(guest.trySteal(holder),"靠近后不能直接偷取背包中的玩偶");
                helper.assertTrue(holder.getInventory().getItem(slot).getCount()==1 && !guest.isCarryingDoll(),"背包物品及归属保持不变");
                guest.snapTo(helper.absoluteVec(new Vec3(12.5,1,12.5)));
                holder.getInventory().setItem(slot,ItemStack.EMPTY);
                guest.customServerAiStep(helper.getLevel());
                guest.getNavigation().tick();
                helper.assertTrue(guest.getMoveControl().getSpeedModifier()==1,"移除最后一件玩偶后恢复普通寻路");
                helper.assertTrue(guest.removeWhenFarAway(256*256),"失去目标后恢复原有远距离消失规则");
            }
            holder.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.customServerAiStep(helper.getLevel());
            guest.getNavigation().tick();
            helper.assertTrue(guest.getMoveControl().getSpeedModifier()==2,"副手玩偶同样触发跟随");
            holder.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            holder.getInventory().setItem(35,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.customServerAiStep(helper.getLevel());
            // 没有其他持有者时保持范围外原目标；另一人拿到玩偶且更近时立即改向。
            holder.snapTo(helper.absoluteVec(new Vec3(30.5,1,12.5)));
            guest.customServerAiStep(helper.getLevel());
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(holder.blockPosition()),"没有更近者时继续跟随初始范围外的原玩家");
            other.getInventory().setItem(9,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.customServerAiStep(helper.getLevel());
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(other.blockPosition()),"另一持有者更近时改为跟随该玩家");
            holder.snapTo(helper.absoluteVec(new Vec3(8.5,1,12.5)));
            guest.customServerAiStep(helper.getLevel());
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(holder.blockPosition()),"原玩家重新变近时也能再次切换目标");
            holder.snapTo(helper.absoluteVec(new Vec3(6.5,1,12.5)));
            guest.customServerAiStep(helper.getLevel());
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(holder.blockPosition()),"两名持有者等距时保持当前目标");
            holder.getInventory().setItem(35,ItemStack.EMPTY);
            guest.customServerAiStep(helper.getLevel());
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(other.blockPosition()),"原目标失去玩偶后可选择另一位玩家");
            other.setGameMode(GameType.SPECTATOR);
            guest.customServerAiStep(helper.getLevel());
            guest.getNavigation().tick();
            helper.assertTrue(guest.getMoveControl().getSpeedModifier()==1,"旁观者不会触发跟随");
            other.getInventory().setItem(9,ItemStack.EMPTY); other.setGameMode(GameType.SURVIVAL);
            holder.snapTo(helper.absoluteVec(new Vec3(3.5,1,12.5)));
            holder.setGameMode(GameType.CREATIVE);
            holder.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.customServerAiStep(helper.getLevel());
            guest.getNavigation().tick();
            helper.assertTrue(guest.getMoveControl().getSpeedModifier()==2,"创造玩家也能触发跟随");
            guest.snapTo(helper.absoluteVec(new Vec3(4.5,1,12.5)));
            helper.assertFalse(guest.trySteal(holder),"创造玩家的手持玩偶仍不能被偷走");
            holder.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(guest.trySteal(holder),"生存玩家的手持玩偶仍可正常偷取");
            guest.discard();
        }
        helper.destroyBlock(idlePos); remove(helper,holder); remove(helper,other); clean(helper); griefing(helper,originalGriefing); helper.succeed();
    }
}
