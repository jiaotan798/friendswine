package com.friendswine;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
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
        var entity=type(variant).create(helper.getLevel());
        Vec3 pos=helper.absoluteVec(relative);
        entity.moveTo(pos.x,pos.y,pos.z,0,0); entity.setNoAi(noAi); entity.setOnGround(true);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
    private static ServerPlayer player(GameTestHelper helper,Vec3 relative) {
        var server=helper.getLevel().getServer();
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"GuestGameTest"),false);
        ServerPlayer player=new ServerPlayer(server,helper.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection); server.getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(relative)); return player;
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
        player.moveTo(helper.absoluteVec(relative)); return player;
    }

    private static boolean griefing(GameTestHelper helper) {
        return helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }
    private static void griefing(GameTestHelper helper,boolean enabled) {
        helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(enabled,helper.getLevel().getServer());
    }
    private static void remove(GameTestHelper helper,ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    @GameTest(template="friendswine:doll_range",batch="friendswine-guest-eggs",timeoutTicks=100)
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
        long originalTime=helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(1000);
        BlockPos pos=new BlockPos(20,1,5),absolute=helper.absolutePos(pos);
        helper.setBlock(pos.below(),Blocks.GRASS_BLOCK);
        for (var type:List.of(FriendsWine.KASUMI.get(),FriendsWine.EMMA.get(),FriendsWine.AMIYA.get())) {
            helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.getPlacementType(type)==net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,"All three registered types use on-ground spawn placement");
            helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type,helper.getLevel(),MobSpawnType.NATURAL,absolute,RandomSource.create(1)),"Registered natural spawn entry point accepts dry animal ground");
            helper.assertTrue(PartyGuestEntity.checkNaturalSpawn(type,helper.getLevel(),MobSpawnType.NATURAL,absolute,RandomSource.create(1)),"Daylit dry animal-spawnable land permits rare natural spawning");
            helper.setBlock(pos.below(),Blocks.STONE);
            helper.assertFalse(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type,helper.getLevel(),MobSpawnType.NATURAL,absolute,RandomSource.create(1)),"Registered natural spawn entry point rejects stone ground");
            helper.assertFalse(PartyGuestEntity.checkNaturalSpawn(type,helper.getLevel(),MobSpawnType.NATURAL,absolute,RandomSource.create(1)),"Stone is not animal-spawnable ground");
            helper.setBlock(pos.below(),Blocks.GRASS_BLOCK); helper.setBlock(pos,Blocks.WATER);
            helper.assertFalse(PartyGuestEntity.checkNaturalSpawn(type,helper.getLevel(),MobSpawnType.NATURAL,absolute,RandomSource.create(1)),"Water rejects natural spawning");
            helper.setBlock(pos,Blocks.AIR);
            ServerLevel nether=helper.getLevel().getServer().getLevel(Level.NETHER);
            if (nether!=null) helper.assertFalse(PartyGuestEntity.checkNaturalSpawn(type,nether,MobSpawnType.NATURAL,absolute,RandomSource.create(1)),"Natural spawning is Overworld only");
            ServerLevel end=helper.getLevel().getServer().getLevel(Level.END);
            if (end!=null) helper.assertFalse(PartyGuestEntity.checkNaturalSpawn(type,end,MobSpawnType.NATURAL,absolute,RandomSource.create(1)),"Natural spawning rejects the End");
        }
        helper.getLevel().setDayTime(originalTime);
        remove(helper,player); clean(helper); helper.succeed();
    }

    /** 覆盖第三档生成、2／4 只边界、已有个体占位、重复切档及存档恢复。 */
    @GameTest(template="friendswine:doll_range",batch="friendswine-guest-spawning",timeoutTicks=100)
    public static void stageSpawnAndCombinedCap(GameTestHelper helper) {
        prepare(helper); helper.setBlock(DOLL,FriendsWine.DOLL.get());
        DollBlockEntity doll=(DollBlockEntity)helper.getBlockEntity(DOLL);
        ServerPlayer player=player(helper,new Vec3(2.5,1,2.5));
        boolean seenKasumi=false,seenEmma=false,seenAmiya=false,seenTwo=false,seenFour=false;
        for (int seed=0;seed<32;seed++) {
            clean(helper); doll.stopPlaying(); helper.getLevel().random.setSeed(seed);
            boolean remote=(seed&1)==1;
            if (remote) remoteFirstStage(helper,player); else clickDoll(helper,player);
            helper.assertTrue(doll.getMode()==(remote ? DollBlockEntity.SQUASH_ONLY : DollBlockEntity.FULL),"Right click starts stage two; the remote's first press starts stage one");
            helper.assertTrue(guests(helper).isEmpty(),"Ordinary right click and the remote's first stage never spawn guests");
            long song=doll.getStartTick();
            helper.assertFalse(doll.startPlaying(),"Starting an already-playing doll is rejected");
            helper.assertTrue(guests(helper).isEmpty() && doll.getStartTick()==song,"Repeated start neither spawns nor resets the song");
            if (remote) {
                doll.cycleRemote();
                helper.assertTrue(doll.getMode()==DollBlockEntity.FULL && guests(helper).isEmpty(),"Changing stage one to two does not spawn");
            }
            doll.cycleRemote();
            List<PartyGuestEntity> spawned=guests(helper);
            int count=spawned.size();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && doll.getStartTick()==song,"Entering stage three preserves the song");
            helper.assertTrue(count>=2 && count<=4,"Only entering third-stage orbit creates two to four combined guests; seed="+seed+", count="+count);
            seenKasumi|=spawned.stream().anyMatch(e->e.variant()==GuestVariant.KASUMI);
            seenEmma|=spawned.stream().anyMatch(PartyGuestEntity::isEmma);
            seenAmiya|=spawned.stream().anyMatch(e->e.variant()==GuestVariant.AMIYA);
            seenTwo|=count==2; seenFour|=count==4;
            for (PartyGuestEntity created:spawned) {
                created.setNoAi(true);
                helper.assertTrue(created.position().distanceToSqr(Vec3.atCenterOf(helper.absolutePos(DOLL)))<=256,"Third-stage guests spawn within sixteen blocks");
                helper.assertTrue(helper.getLevel().noCollision(created),"Third-stage spawning uses safe non-colliding ground");
            }
            CompoundTag saved=doll.getUpdateTag(helper.getLevel().registryAccess());
            doll.setRemoved();
            doll.loadAdditional(saved,helper.getLevel().registryAccess());
            doll.clearRemoved();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && doll.getStartTick()==song,"Actual unload/load restores the saved stage and clock");
            helper.assertTrue(guests(helper).size()==count,"Loading an orbiting doll never repeats third-stage spawning");
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
        PartyGuestEntity natural=FriendsWine.EMMA.get().spawn(helper.getLevel(),helper.absolutePos(new BlockPos(6,1,8)),MobSpawnType.NATURAL);
        helper.assertTrue(natural!=null && natural.isEmma(),"A naturally initialized existing guest participates in the shared cap");
        natural.setNoAi(true);
        clickDoll(helper,player);
        helper.assertTrue(guests(helper).size()==2,"Starting stage two leaves existing egg and natural guests unchanged");
        doll.cycleRemote();
        helper.assertTrue(guests(helper).size()==4 && guests(helper).contains(fromEgg) && guests(helper).contains(natural),"Egg and natural individuals count toward the shared four-guest third-stage cap");
        int previous=guests(helper).size();
        for (var created:guests(helper)) created.setNoAi(true);
        for (int repeat=0;repeat<10;repeat++) {
            doll.stopPlaying(); clickDoll(helper,player);
            helper.assertTrue(guests(helper).size()==previous,"Repeated stage-two starts never add guests");
            doll.cycleRemote();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && guests(helper).size()==previous,"Repeated third-stage entry cannot exceed the existing four-guest cap");
        }
        doll.stopPlaying(); clean(helper);

        for (int i=0;i<3;i++) guest(helper,GuestVariant.values()[i%3],new Vec3(4.5+i,1,5.5),true);
        clickDoll(helper,player);
        helper.assertTrue(guests(helper).size()==3,"Stage two never fills available guest capacity");
        doll.cycleRemote();
        helper.assertTrue(guests(helper).size()==4,"Three preexisting combined guests leave exactly one third-stage spawn slot");
        doll.stopPlaying(); clickDoll(helper,player);
        doll.cycleRemote();
        helper.assertTrue(guests(helper).size()==4,"Four existing guests prevent further third-stage spawning");
        doll.stopPlaying(); clean(helper);

        for (BlockPos pos:BlockPos.betweenClosed(4,-2,4,20,5,20)) helper.setBlock(pos,Blocks.STONE);
        helper.setBlock(DOLL,FriendsWine.DOLL.get());
        doll=(DollBlockEntity)helper.getBlockEntity(DOLL);
        clickDoll(helper,player);
        helper.assertTrue(doll.getMode()==DollBlockEntity.FULL && guests(helper).isEmpty(),"Blocked terrain still starts playback but never forces guests into solid blocks");
        doll.cycleRemote();
        helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && guests(helper).isEmpty(),"Third-stage spawning never forces guests into blocked terrain");
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

    @GameTest(template="friendswine:doll_range",batch="friendswine-guest-theft",timeoutTicks=100)
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
            player.moveTo(helper.absoluteVec(new Vec3(10.5,1,8.5)));
            helper.assertFalse(guest.trySteal(player),"Theft cannot occur beyond 1.5 blocks");
            player.moveTo(helper.absoluteVec(new Vec3(9.5,1,8.5)));
            helper.setBlock(new BlockPos(9,1,8),Blocks.STONE); helper.setBlock(new BlockPos(9,2,8),Blocks.STONE);
            helper.assertFalse(guest.trySteal(player),"A wall prevents line-of-sight theft");
            helper.setBlock(new BlockPos(9,1,8),Blocks.AIR); helper.setBlock(new BlockPos(9,2,8),Blocks.AIR);
            helper.setBlock(DOLL,FriendsWine.DOLL.get()); DollBlockEntity doll=(DollBlockEntity)helper.getBlockEntity(DOLL); doll.startPlaying(); cleanExcept(helper,guest);
            helper.assertFalse(guest.trySteal(player),"An active nearby doll takes priority over a holder");
            helper.assertTrue(player.getMainHandItem().getCount()==1,"Failed guards never consume a player's item");
            helper.assertTrue(victimSounds.size()==successes && nearbySounds.size()==successes && farSounds.isEmpty(),"副手和冒险模式成功时各播放一次，全部失败分支不播放且远处玩家收不到");
            doll.stopPlaying(); helper.destroyBlock(DOLL); guest.discard();
        }
        griefing(helper,originalGriefing); remove(helper,player); remove(helper,nearby); remove(helper,far); clean(helper); helper.succeed();
    }

    @GameTest(template="friendswine:doll_range",batch="friendswine-guest-carry",timeoutTicks=100)
    public static void carrySavePlacementAndDeath(GameTestHelper helper) {
        prepare(helper);
        boolean originalGriefing=griefing(helper); griefing(helper,true);
        boolean originalMobLoot=helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        for (GuestVariant variant:GuestVariant.values()) {
            PartyGuestEntity original=guest(helper,variant,new Vec3(12.5,1,12.5),true);
            ItemStack named=new ItemStack(FriendsWine.DOLL_ITEM.get()); named.set(DataComponents.CUSTOM_NAME,Component.literal("Guest carried doll"));
            original.setItemSlot(EquipmentSlot.MAINHAND,named);
            CompoundTag saved=original.saveWithoutId(new CompoundTag()); original.discard();
            PartyGuestEntity restored=type(variant).create(helper.getLevel());
            restored.load(saved); restored.setUUID(UUID.randomUUID()); helper.getLevel().addFreshEntity(restored);
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
            helper.assertTrue(guests(helper).size()==1 && guests(helper).contains(restored),"NPC placement starts stage two without creating more guests");
            cleanExcept(helper,restored);
            helper.assertTrue(doll.getBlockPos().distSqr(restored.blockPosition())<=16,"Placed doll remains within the four-block search");
            helper.assertFalse(restored.tryPlaceCarried(helper.getLevel()),"An empty hand cannot place another doll");
            doll.cycleRemote();
            helper.assertTrue(doll.getMode()==DollBlockEntity.ORBIT && guests(helper).size()>=3 && guests(helper).size()<=4,"Entering the NPC-placed doll's third stage creates two-to-three guests while counting its carrier toward four");
            cleanExcept(helper,restored);
            doll.stopPlaying(); helper.getLevel().destroyBlock(doll.getBlockPos(),false);
            restored.setItemSlot(EquipmentSlot.MAINHAND,named.copy());
            for (ItemEntity item:helper.getLevel().getEntitiesOfClass(ItemEntity.class,restored.getBoundingBox().inflate(8))) item.discard();
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(variant!=GuestVariant.EMMA,helper.getLevel().getServer());
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
        helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(originalMobLoot,helper.getLevel().getServer());
        griefing(helper,originalGriefing); helper.succeed();
    }

    @GameTest(template="friendswine:doll_range",batch="friendswine-guest-ai",timeoutTicks=300)
    public static void realAiHolderAndIdleDoll(GameTestHelper helper) {
        prepare(helper);
        boolean originalGriefing=griefing(helper); griefing(helper,true);
        ServerPlayer holder=player(helper,new Vec3(3.5,1,12.5));
        holder.getInventory().setItem(35,new ItemStack(FriendsWine.DOLL_ITEM.get()));
        BlockPos idlePos=new BlockPos(20,1,12);
        helper.setBlock(idlePos,FriendsWine.DOLL.get());
        PartyGuestEntity guest=guest(helper,GuestVariant.KASUMI,new Vec3(7.5,1,12.5),false);
        Vec3 start=guest.position();
        int[] placementPopulation={0};
        helper.onEachTick(()->{
            placementPopulation[0]=Math.max(placementPopulation[0],guests(helper).size());
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
            helper.assertTrue(placementPopulation[0]==1 && guests(helper).size()==1,"Actual NPC AI placement never creates guests in stage two");
            placed.cycleRemote();
            helper.assertTrue(placed.getMode()==DollBlockEntity.ORBIT && guests(helper).size()>=3 && guests(helper).size()<=4,"The AI-placed doll spawns guests only when its third stage is entered");
            cleanExcept(helper,guest);
            placed.stopPlaying(); helper.getLevel().destroyBlock(placed.getBlockPos(),false);
            guest.moveTo(helper.absoluteVec(new Vec3(7.5,1,12.5))); guest.setDeltaMovement(Vec3.ZERO);
        }).thenIdle(30).thenExecute(()->{
            helper.assertTrue(guest.getX()>start.x+0.1,"With no holder or active doll, normal AI approaches the loaded idle doll");
            guest.moveTo(helper.absoluteVec(new Vec3(4.5,1,12.5))); guest.setDeltaMovement(Vec3.ZERO);
            holder.moveTo(helper.absoluteVec(new Vec3(3.5,1,12.5)));
            holder.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            DollBlockEntity idle=(DollBlockEntity)helper.getBlockEntity(idlePos); idle.startPlaying(); cleanExcept(helper,guest);
        }).thenIdle(30).thenExecute(()->{
            helper.assertTrue(holder.getMainHandItem().getCount()==1,"Actual active-doll AI priority leaves the adjacent holder's item untouched");
            helper.assertTrue(guest.getX()>helper.absoluteVec(new Vec3(4.5,1,12.5)).x+0.1,"Active doll attraction moves the guest toward the doll");
            ((DollBlockEntity)helper.getBlockEntity(idlePos)).stopPlaying();
            clean(helper); remove(helper,holder); griefing(helper,originalGriefing);
        }).thenSucceed();
    }

    /** 验证 10 点生命、原版伤害与击退，并覆盖普通死亡／管理员清理时的携带返还。 */
    @GameTest(template="friendswine:doll_range",batch="friendswine-guest-immunity",timeoutTicks=100)
    public static void immunityAndCleanup(GameTestHelper helper) {
        prepare(helper);
        ServerPlayer attacker=player(helper,new Vec3(3.5,1,8.5));
        boolean originalMobLoot=helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false,helper.getLevel().getServer());
        for (GuestVariant variant:GuestVariant.values()) {
            PartyGuestEntity guest=guest(helper,variant,new Vec3(8.5,1,8.5),true);
            guest.setNoGravity(true);
            helper.assertTrue(guest.getMaxHealth()==10 && guest.getHealth()==10,"三种角色生命上限均为 10 点（5 颗心）");
            helper.assertTrue(guest.isAttackable(),"三种角色恢复原版可攻击判定");
            var sources=guest.damageSources();
            // 每次恢复生命并清除原版短暂无敌帧，单独验证各类伤害，而不是累计伤害造成死亡。
            for (var source:List.of(sources.playerAttack(attacker),sources.mobAttack(attacker),sources.arrow(null,attacker),
                    sources.explosion(attacker,attacker),sources.fall(),sources.inFire(),sources.lava(),sources.drown(),sources.generic())) {
                guest.setHealth(10); guest.invulnerableTime=0;
                helper.assertTrue(guest.hurt(source,1),"常规伤害必须恢复："+variant+"/"+source);
                helper.assertTrue(guest.getHealth()==9,"无护甲角色按原版扣除一点生命");
            }
            guest.setHealth(10); guest.invulnerableTime=0;
            attacker.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            attacker.attack(guest);
            helper.assertTrue(guest.getHealth()<10,"真实玩家近战可伤害角色");
            guest.setDeltaMovement(Vec3.ZERO); guest.knockback(1,1,0);
            helper.assertTrue(guest.getDeltaMovement().horizontalDistanceSqr()>1e-8,"恢复原版横向击退");
            guest.setHealth(10); guest.invulnerableTime=0; guest.setDeltaMovement(Vec3.ZERO);
            // 使用小当量且不破坏方块的真实爆炸，验证伤害／推力并避免破坏后续清理测试的地面。
            helper.getLevel().explode(attacker,guest.getX()-0.25,guest.getY()+guest.getBbHeight()/2,guest.getZ(),0.5F,Level.ExplosionInteraction.NONE);
            helper.assertTrue(guest.getHealth()<10 && guest.getDeltaMovement().lengthSqr()>1e-8,"真实爆炸恢复伤害和推力");
            guest.setHealth(10); guest.invulnerableTime=0;
            helper.assertTrue(guest.hurt(sources.fellOutOfWorld(),1) && guest.getHealth()==9,"虚空伤害继续生效");
            guest.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.kill();
            helper.assertTrue(!guest.isAlive() && guest.getMainHandItem().isEmpty(),"真实 /kill 仍可清理并释放携带物");
            int drops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,guest.getBoundingBox().inflate(3)).stream()
                    .filter(item->item.getItem().is(FriendsWine.DOLL_ITEM.get())).mapToInt(item->item.getItem().getCount()).sum();
            helper.assertTrue(drops==1,"关闭生物掉落时 /kill 也只返还一个携带玩偶");
            for (var item:helper.getLevel().getEntitiesOfClass(ItemEntity.class,guest.getBoundingBox().inflate(3))) item.discard();
            guest.discard();
            // 不仅管理员清理，普通受伤死亡也必须返还玩家被偷走的玩偶。
            PartyGuestEntity victim=guest(helper,variant,new Vec3(8.5,1,8.5),true);
            victim.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            helper.assertTrue(victim.hurt(victim.damageSources().playerAttack(attacker),20) && !victim.isAlive(),"普通攻击可以杀死 10 点生命的角色");
            int deathDrops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,victim.getBoundingBox().inflate(3)).stream()
                    .filter(item->item.getItem().is(FriendsWine.DOLL_ITEM.get())).mapToInt(item->item.getItem().getCount()).sum();
            helper.assertTrue(deathDrops==1 && victim.getMainHandItem().isEmpty(),"普通死亡只返还一件携带玩偶，不因关闭生物掉落而丢失或复制");
            for (var item:helper.getLevel().getEntitiesOfClass(ItemEntity.class,victim.getBoundingBox().inflate(3))) item.discard();
            victim.discard();
        }
        helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(originalMobLoot,helper.getLevel().getServer());
        remove(helper,attacker); clean(helper); helper.succeed();
    }

    /** 验证三角色的背包／双手跟随、就近切换、等距保持与远距离兜底，以及跟随与偷取条件的分离。 */
    @GameTest(template="friendswine:doll_range",batch="friendswine-guest-speed",timeoutTicks=100)
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
                guest.customServerAiStep();
                guest.getNavigation().tick();
                helper.assertFalse(guest.getNavigation().isDone(),"背包玩偶能建立真实跟随路径："+variant+"/slot="+slot);
                helper.assertTrue(guest.getMoveControl().getSpeedModifier()==2,"背包跟随也使用 2 倍速度");
                helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(holder.blockPosition()),"导航目标为背包持有者");
                helper.assertFalse(guest.removeWhenFarAway(256*256),"跟随期间免于远距离自然消失");
                helper.assertTrue(guest.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue()==0.25,"基础速度保持 0.25");
                guest.moveTo(helper.absoluteVec(new Vec3(4.5,1,12.5)));
                helper.assertFalse(guest.trySteal(holder),"靠近后不能直接偷取背包中的玩偶");
                helper.assertTrue(holder.getInventory().getItem(slot).getCount()==1 && !guest.isCarryingDoll(),"背包物品及归属保持不变");
                guest.moveTo(helper.absoluteVec(new Vec3(12.5,1,12.5)));
                holder.getInventory().setItem(slot,ItemStack.EMPTY);
                guest.customServerAiStep();
                guest.getNavigation().tick();
                helper.assertTrue(guest.getMoveControl().getSpeedModifier()==1,"移除最后一件玩偶后恢复普通寻路");
                helper.assertTrue(guest.removeWhenFarAway(256*256),"失去目标后恢复原有远距离消失规则");
            }
            holder.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.customServerAiStep();
            guest.getNavigation().tick();
            helper.assertTrue(guest.getMoveControl().getSpeedModifier()==2,"副手玩偶同样触发跟随");
            holder.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            holder.getInventory().setItem(35,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.customServerAiStep();
            // 没有其他持有者时保持范围外原目标；另一人拿到玩偶且更近时立即改向。
            holder.moveTo(helper.absoluteVec(new Vec3(30.5,1,12.5)));
            guest.customServerAiStep();
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(holder.blockPosition()),"没有更近者时继续跟随初始范围外的原玩家");
            other.getInventory().setItem(9,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.customServerAiStep();
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(other.blockPosition()),"另一持有者更近时改为跟随该玩家");
            holder.moveTo(helper.absoluteVec(new Vec3(8.5,1,12.5)));
            guest.customServerAiStep();
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(holder.blockPosition()),"原玩家重新变近时也能再次切换目标");
            holder.moveTo(helper.absoluteVec(new Vec3(6.5,1,12.5)));
            guest.customServerAiStep();
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(holder.blockPosition()),"两名持有者等距时保持当前目标");
            holder.getInventory().setItem(35,ItemStack.EMPTY);
            guest.customServerAiStep();
            helper.assertTrue(guest.getNavigation().getPath().getTarget().equals(other.blockPosition()),"原目标失去玩偶后可选择另一位玩家");
            other.setGameMode(GameType.SPECTATOR);
            guest.customServerAiStep();
            guest.getNavigation().tick();
            helper.assertTrue(guest.getMoveControl().getSpeedModifier()==1,"旁观者不会触发跟随");
            other.getInventory().setItem(9,ItemStack.EMPTY); other.setGameMode(GameType.SURVIVAL);
            holder.moveTo(helper.absoluteVec(new Vec3(3.5,1,12.5)));
            holder.setGameMode(GameType.CREATIVE);
            holder.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FriendsWine.DOLL_ITEM.get()));
            guest.customServerAiStep();
            guest.getNavigation().tick();
            helper.assertTrue(guest.getMoveControl().getSpeedModifier()==2,"创造玩家也能触发跟随");
            guest.moveTo(helper.absoluteVec(new Vec3(4.5,1,12.5)));
            helper.assertFalse(guest.trySteal(holder),"创造玩家的手持玩偶仍不能被偷走");
            holder.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(guest.trySteal(holder),"生存玩家的手持玩偶仍可正常偷取");
            guest.discard();
        }
        helper.destroyBlock(idlePos); remove(helper,holder); remove(helper,other); clean(helper); griefing(helper,originalGriefing); helper.succeed();
    }

    private static PartyGuestEntity cancelledDeath;
    private static boolean deathListenerInstalled;

    private static void scope(DeathFlashScope scope) { ServerConfig.DEATH_FLASH_SCOPE = scope; }
    private static boolean flashPacket(Object packet) { return packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket payload && payload.payload() instanceof GuestDeathNetworking.Event; }
    private static long flashCount(List<Object> packets) { return packets.stream().filter(PartyGuestGameTests::flashPacket).count(); }
    private static long announcementCount(List<Object> packets) {
        return packets.stream().filter(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat
                && chat.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                && contents.getKey().equals("message.friendswine.guestKilled")).count();
    }
    private static void hit(GameTestHelper helper, PartyGuestEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
        entity.invulnerableTime = 0;
        entity.hurt(source, amount);
    }
    private static ServerPlayer deathPlayer(GameTestHelper helper, Vec3 relative, List<Object> packets) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "DeathFlashTest"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        channel.pipeline().addLast(new io.netty.channel.ChannelOutboundHandlerAdapter() {
            @Override public void write(io.netty.channel.ChannelHandlerContext context, Object message, io.netty.channel.ChannelPromise promise) throws Exception {
                packets.add(message);
                super.write(context, message, promise);
            }
        });
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(relative)); return player;
    }

    /** 真正杀死三个角色，截获公屏消息与闪屏包；清理、取消死亡和重复回调必须保持静默。 */
    @GameTest(template="friendswine:doll_range",batch="friendswine-guest-death",timeoutTicks=100)
    public static void deathFlashAndAnnouncement(GameTestHelper helper) {
        prepare(helper);
        checkScopePersistence(helper);
        if (!deathListenerInstalled) { net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> entity != cancelledDeath); deathListenerInstalled = true; }
        DeathFlashScope previous = ServerConfig.deathFlashScope();
        var nearPackets = new java.util.ArrayList<Object>();
        var farPackets = new java.util.ArrayList<Object>();
        var killer = deathPlayer(helper, new Vec3(8.5,1,8.5), nearPackets);
        var far = deathPlayer(helper, new Vec3(80.5,1,8.5), farPackets);
        try {
            for (var variant : GuestVariant.values()) {
                scope(DeathFlashScope.NEARBY);
                var victim = guest(helper, variant, new Vec3(10.5,1,8.5), true);
                far.moveTo(victim.position().add(48,0,0));
                helper.assertTrue(GuestDeathNetworking.recipients(victim, DeathFlashScope.NEARBY).contains(far), "48 格边界包含在内");
                far.moveTo(victim.position().add(48.01,0,0));
                helper.assertFalse(GuestDeathNetworking.recipients(victim, DeathFlashScope.NEARBY).contains(far), "超过48格不接收");
                far.moveTo(victim.position().add(0,48.01,0));
                helper.assertFalse(GuestDeathNetworking.recipients(victim, DeathFlashScope.NEARBY).contains(far), "垂直距离同样参与球形范围");
                nearPackets.clear(); farPackets.clear();
                victim.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FriendsWine.DOLL_ITEM.get()));
                hit(helper, victim, victim.damageSources().playerAttack(killer), 20);
                helper.assertTrue(!victim.isAlive(), "正常近战能杀死十点生命的角色");
                helper.assertTrue(flashCount(nearPackets)==1 && flashCount(farPackets)==0, "附近模式只给范围内玩家发送闪屏");
                helper.assertTrue(announcementCount(nearPackets)==1 && announcementCount(farPackets)==1, "范围外仍收到一次全服播报");
                victim.die(victim.damageSources().playerAttack(killer));
                helper.assertTrue(flashCount(nearPackets)==1 && announcementCount(farPackets)==1, "重复死亡通知不重复触发");
                helper.assertTrue(victim.getMainHandItem().isEmpty(), "死亡效果不能阻断携带玩偶返还");
                victim.discard();

                scope(DeathFlashScope.GLOBAL);
                nearPackets.clear(); farPackets.clear();
                victim = guest(helper, variant, new Vec3(10.5,1,8.5), true);
                hit(helper, victim, victim.damageSources().arrow(null,killer), 20);
                helper.assertTrue(flashCount(nearPackets)==1 && flashCount(farPackets)==1, "全服模式远程击杀也给远处玩家发事件");
                helper.assertTrue(announcementCount(farPackets)==1, "每次有效击杀单独播报");
                scope(DeathFlashScope.NEARBY);
                helper.assertTrue(flashCount(farPackets)==1, "修改配置不会撤销已经发出的事件");
                victim.discard();

                nearPackets.clear(); farPackets.clear();
                victim = guest(helper, variant, new Vec3(10.5,1,8.5), true);
                hit(helper, victim, victim.damageSources().playerAttack(killer), 1);
                victim.kill();
                victim.discard();
                victim = guest(helper, variant, new Vec3(10.5,1,8.5), true);
                hit(helper, victim, victim.damageSources().playerAttack(killer), 1);
                hit(helper, victim, victim.damageSources().fellOutOfWorld(), 20);
                victim.discard();
                victim = guest(helper, variant, new Vec3(10.5,1,8.5), true);
                hit(helper, victim, victim.damageSources().generic(), 20);
                victim.discard();
                victim = guest(helper, variant, new Vec3(10.5,1,8.5), true);
                victim.discard();
                helper.assertTrue(flashCount(nearPackets)==0 && announcementCount(farPackets)==0, "管理员／虚空清理、无归属环境死亡和直接移除均不触发");

                cancelledDeath = guest(helper, variant, new Vec3(10.5,1,8.5), true);
                hit(helper, cancelledDeath, cancelledDeath.damageSources().playerAttack(killer), 20);
                helper.assertTrue(flashCount(nearPackets)==0 && announcementCount(farPackets)==0, "加载器取消死亡后不能提前播报或闪屏");
                cancelledDeath.discard(); cancelledDeath = null;
            }
            helper.succeed();
        } finally {
            cancelledDeath = null; scope(previous);
            remove(helper,killer); remove(helper,far); clean(helper);
        }
    }

    /** 独立 GameTest 工作目录中的真实配置往返；finally 按原始字节恢复，不污染其他用例。 */
    private static void checkScopePersistence(GameTestHelper helper) {
        var path = ConfigFile.path("server");
        byte[] original;
        try { original = java.nio.file.Files.exists(path) ? java.nio.file.Files.readAllBytes(path) : null; }
        catch (java.io.IOException failure) { throw new AssertionError(failure); }
        try {
            var old = new com.google.gson.JsonObject();
            old.addProperty("orbitSpeed", .75);
            old.addProperty("unrelatedExistingValue", "keep");
            ConfigFile.write("server", old); ServerConfig.load();
            helper.assertTrue(ServerConfig.deathFlashScope()==DeathFlashScope.NEARBY && ServerConfig.ORBIT_SPEED.get()==.75, "旧配置缺省范围，保留环绕值");
            ServerConfig.save(.75, DeathFlashScope.GLOBAL); ServerConfig.load();
            helper.assertTrue(ServerConfig.deathFlashScope()==DeathFlashScope.GLOBAL && ServerConfig.ORBIT_SPEED.get()==.75, "全服范围保存后重载恢复");
            ServerConfig.save(.5); ServerConfig.load();
            helper.assertTrue(ServerConfig.deathFlashScope()==DeathFlashScope.GLOBAL && ServerConfig.ORBIT_SPEED.get()==.5, "旧环绕修改入口不能重置范围");
            helper.assertTrue(ConfigFile.read("server").get("unrelatedExistingValue").getAsString().equals("keep"), "保存保留无关旧键");
            old = ConfigFile.read("server"); old.addProperty("deathFlashScope", "invalid");
            ConfigFile.write("server", old); ServerConfig.load();
            helper.assertTrue(ServerConfig.deathFlashScope()==DeathFlashScope.NEARBY && ServerConfig.ORBIT_SPEED.get()==.5, "非法磁盘枚举回退附近，不重置其他字段");
        } finally {
            try {
                if (original == null) java.nio.file.Files.deleteIfExists(path);
                else java.nio.file.Files.write(path, original);
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
            ServerConfig.load();
        }
    }
}
