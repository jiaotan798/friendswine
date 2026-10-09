package com.friendswine;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

public final class WineGameTests {
    public WineGameTests() {}

    public static ServerPlayer mockServerPlayer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FriendsWineQA"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    private static EmbeddedChannel channel(ServerPlayer player) {
        try {
            var connection = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("connection");
            connection.setAccessible(true);
            var channel = net.minecraft.network.Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            return (EmbeddedChannel) channel.get(connection.get(player.connection));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_empty", batch = "friendswine-dollAnimationPriority", timeoutTicks = 100)
    public static void dollAnimationPriority(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        BlockPos nearPos = new BlockPos(8, 3, 8);
        BlockPos farPos = new BlockPos(15, 3, 8);
        helper.setBlock(nearPos, FriendsWine.DOLL.get());
        helper.setBlock(farPos, FriendsWine.DOLL.get());
        DollBlockEntity near = ((DollBlockEntity) helper.getBlockEntity(nearPos));
        DollBlockEntity far = ((DollBlockEntity) helper.getBlockEntity(farPos));
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(nearPos));
        Vec3 inside = center.add(2, 0, 0);
        ServerPlayer player = WineGameTests.mockServerPlayer(helper);
        player.setNoGravity(true);
        player.moveTo(inside);
        helper.onEachTick(player::doTick);
        int[] remaining = new int[1];
        long[] start = new long[1];
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(EntityAnimation.forEntity(player, 0.4F) == null,
                            "No active doll or wine must select no animation");
                    helper.assertTrue(EntityAnimation.forCamera(player, 0.4F) == null, "Idle camera must stay vanilla");
                    player.addEffect(new MobEffectInstance(FriendsWine.TIPSY, WineItem.DURATION_TICKS));
                })
                .thenIdle(9)
                .thenExecute(() -> {
                    assertWineAnimation(helper, player);
                    remaining[0] = player.getEffect(FriendsWine.TIPSY).getDuration();
                    near.cycleRemote();
                    far.startPlaying(); PartyGuestGameTests.clean(helper);
                    start[0] = near.getStartTick();
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    assertDollAnimation(helper, player, near, false);
                    helper.assertTrue(player.getEffect(FriendsWine.TIPSY).getDuration() < remaining[0] - 5,
                            "Wine must continue counting down while the nearest squash-only doll wins");
                    near.cycleRemote();
                    helper.assertTrue(near.getStartTick() == start[0], "Adding rotation must keep the squash timeline");
                    assertDollAnimation(helper, player, near, true);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    assertDollAnimation(helper, player, near, true);
                    helper.assertTrue(EntityAnimation.forEntity(player, 0.4F).rotationSeconds()
                                    < EntityAnimation.forEntity(player, 0.4F).seconds(),
                            "The full phase must use the doll's separate rotation start, not the wine clock");
                    player.moveTo(helper.absoluteVec(new Vec3(30.5, 3.5, 20.5)));
                    assertWineAnimation(helper, player);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    assertWineAnimation(helper, player);
                    helper.assertTrue(player.getEffect(FriendsWine.TIPSY).getDuration() < remaining[0] - 10,
                            "Leaving the radius must resume the remaining wine timeline without restarting");
                    player.moveTo(inside);
                    near.stopPlaying();
                    assertDollAnimation(helper, player, far, true);
                    far.stopPlaying();
                    assertWineAnimation(helper, player);
                    Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), player.level(), player);
                    helper.assertTrue(EntityAnimation.forEntity(player, 0.4F) == null,
                            "Stopped dolls and cleared wine must restore the default model and eye height");
                    near.cycleRemote();
                    assertDollAnimation(helper, player, near, false);
                    near.cycleRemote();
                    assertDollAnimation(helper, player, near, true);
                    player.moveTo(center.add(10, 0, 0));
                    assertDollAnimation(helper, player, near, true);
                    player.moveTo(center.add(10.01, 0, 0));
                    helper.assertTrue(EntityAnimation.forEntity(player, 0.4F) == null,
                            "Without wine, leaving the ten-block radius must clear the eye-height source");
                    player.moveTo(inside);
                    player.addEffect(new MobEffectInstance(FriendsWine.TIPSY, 3));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(player.hasEffect(FriendsWine.TIPSY), "Wine must be able to expire beneath a doll effect");
                    assertDollAnimation(helper, player, near, true);
                    near.stopPlaying();
                    helper.assertTrue(EntityAnimation.forEntity(player, 0.4F) == null,
                            "Stopping the doll after wine expired must leave no animation");
                    helper.destroyBlock(nearPos);
                    helper.destroyBlock(farPos);
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    private static void assertWineAnimation(GameTestHelper helper, ServerPlayer player) {
        EntityAnimation animation = EntityAnimation.forEntity(player, 0.4F);
        double elapsed = (WineItem.DURATION_TICKS - player.getEffect(FriendsWine.TIPSY).getDuration() + 0.4F) / 20.0;
        helper.assertTrue(animation != null && !animation.rotating() && Math.abs(animation.seconds() - elapsed) < 1e-6,
                "Outside active dolls, the model and eye height must use the current non-rotating wine timeline");
        helper.assertTrue(animation.equals(EntityAnimation.forCamera(player, 0.4F)) && !animation.musical(),
                "Wine-only body and camera must share the personal curve");
    }

    private static void assertDollAnimation(GameTestHelper helper, ServerPlayer player, DollBlockEntity doll, boolean rotating) {
        EntityAnimation animation = EntityAnimation.forEntity(player, 0.4F);
        long time = helper.getLevel().getGameTime();
        double elapsed = (doll.getElapsedTicks(time) + 0.4F) / 20.0;
        double rotation = (doll.getRotationElapsedTicks(time) + 0.4F) / 20.0;
        helper.assertTrue(animation != null && animation.rotating() == rotating
                        && Math.abs(animation.seconds() - elapsed) < 1e-6
                        && Math.abs(animation.rotationSeconds() - rotation) < 1e-6,
                "Both visual consumers must use the nearest active doll's squash and rotation clocks");
        helper.assertTrue(animation.musical() && Math.abs(animation.squash() - JellyAnimation.musicSquash(elapsed)) < 1e-6,
                "Doll bodies must align with the current song loop");
        helper.assertTrue(player.hasEffect(FriendsWine.TIPSY)
                        ? animation.equals(EntityAnimation.forCamera(player, 0.4F))
                        : EntityAnimation.forCamera(player, 0.4F) == null,
                "Wine enables the priority doll camera; a doll alone must never bob the camera");
    }

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_empty", batch = "friendswine-observerEffectPackets", timeoutTicks = 120)
    public static void observerEffectPackets(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        ServerPlayer actor = WineGameTests.mockServerPlayer(helper);
        ServerPlayer viewer = WineGameTests.mockServerPlayer(helper);
        Vec3 position = helper.absoluteVec(new Vec3(8.5, 3, 8.5));
        actor.moveTo(position);
        viewer.moveTo(position.add(2, 0, 0));
        actor.setNoGravity(true);
        viewer.setNoGravity(true);
        EmbeddedChannel channel = channel(viewer);
        helper.onEachTick(() -> {
            actor.doTick();
            helper.getLevel().getChunkSource().move(actor);
            helper.getLevel().getChunkSource().move(viewer);
        });
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> {
                    drain(channel);
                    actor.addEffect(new MobEffectInstance(FriendsWine.TIPSY, WineItem.DURATION_TICKS));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(drain(channel).stream().anyMatch(packet -> packet instanceof ClientboundUpdateMobEffectPacket update
                                    && update.getEntityId() == actor.getId() && update.getEffect() == FriendsWine.TIPSY
                                    && update.getEffectDurationTicks() > (WineItem.DURATION_TICKS - 26)),
                            "An existing observer must receive the native effect packet when drinking starts");
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    helper.assertFalse(drain(channel).stream().anyMatch(packet -> packet instanceof ClientboundUpdateMobEffectPacket update
                                    && update.getEntityId() == actor.getId()),
                            "Ordinary countdown ticks must not flood observers with effect packets");
                    actor.removeEffect(FriendsWine.TIPSY);
                    actor.addEffect(new MobEffectInstance(FriendsWine.TIPSY, WineItem.DURATION_TICKS));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(drain(channel).stream().anyMatch(packet -> packet instanceof ClientboundUpdateMobEffectPacket update
                                    && update.getEntityId() == actor.getId() && update.getEffectDurationTicks() > (WineItem.DURATION_TICKS - 6)),
                            "Refreshing a drink must also refresh observers");
                    net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.invoker().onStartTracking(actor, viewer);
                })
                // Vanilla batches outbound packets until the server tick finishes.
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(drain(channel).stream().anyMatch(packet -> packet instanceof ClientboundUpdateMobEffectPacket update
                                    && update.getEntityId() == actor.getId()),
                            "A late observer must receive the current remaining duration");
                    Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), actor.level(), actor);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(drain(channel).stream().anyMatch(packet -> packet instanceof ClientboundRemoveMobEffectPacket remove
                                    && remove.getEntity(actor.level()) == actor && remove.getEffect() == FriendsWine.TIPSY),
                            "Milk must remove the visual effect on observers too");
                    actor.addEffect(new MobEffectInstance(FriendsWine.TIPSY, 3));
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    helper.assertTrue(drain(channel).stream().anyMatch(packet -> packet instanceof ClientboundRemoveMobEffectPacket remove
                                    && remove.getEntity(actor.level()) == actor),
                            "Natural expiration must remove the visual effect on observers");
                    helper.getLevel().getServer().getPlayerList().remove(actor);
                    helper.getLevel().getServer().getPlayerList().remove(viewer);
                })
                .thenSucceed();
    }

    private static List<Object> drain(EmbeddedChannel channel) {
        channel.runPendingTasks();
        List<Object> packets = new ArrayList<>();
        Object packet;
        while ((packet = channel.readOutbound()) != null) packets.add(packet);
        return packets;
    }

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_empty", batch = "friendswine-drinkAndCreativeTab", timeoutTicks = 3700)
    public static void drinkAndCreativeTab(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        ServerPlayer player = WineGameTests.mockServerPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(8.5, 3, 8.5)));
        // Mock players have no inbound movement packets to call doTick; use the real player tick here.
        helper.onEachTick(player::doTick);
        helper.startSequence()
                .thenExecute(() -> {
                    var parameters = new CreativeModeTab.ItemDisplayParameters(helper.getLevel().enabledFeatures(),
                            false, helper.getLevel().registryAccess());
                    FriendsWine.TAB.get().buildContents(parameters);
                    List<Item> contents = FriendsWine.TAB.get().getDisplayItems().stream().map(ItemStack::getItem).toList();
                    helper.assertTrue(contents.equals(List.of(FriendsWine.DOLL_ITEM.get(), FriendsWine.REMOTE.get(), FriendsWine.WINE.get(), FriendsWine.KASUMI_SPAWN_EGG.get(), FriendsWine.EMMA_SPAWN_EGG.get())),
                            "The dedicated tab must contain doll, remote, wine and both spawn eggs in order");
                    for (var key : List.of(CreativeModeTabs.FUNCTIONAL_BLOCKS, CreativeModeTabs.TOOLS_AND_UTILITIES)) {
                        var tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
                        tab.buildContents(parameters);
                        helper.assertFalse(tab.getDisplayItems().stream().anyMatch(stack -> stack.is(FriendsWine.DOLL_ITEM.get())
                                        || stack.is(FriendsWine.REMOTE.get()) || stack.is(FriendsWine.WINE.get())),
                                "Mod items must not also appear in the original vanilla categories");
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FriendsWine.WINE.get(), 2));
                    helper.assertTrue(player.getMainHandItem().getMaxStackSize() == 16, "Wine stack limit must be sixteen");
                    FriendsWine.WINE.get().use(player.level(), player, InteractionHand.MAIN_HAND);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    player.stopUsingItem();
                    helper.assertFalse(player.hasEffect(FriendsWine.TIPSY), "Cancelled drinking must not apply a personal effect");
                    helper.assertTrue(player.getMainHandItem().getCount() == 2, "Cancelled drinking must not consume wine");
                    FriendsWine.WINE.get().use(player.level(), player, InteractionHand.MAIN_HAND);
                })
                .thenIdle(30)
                .thenExecute(() -> helper.assertFalse(player.hasEffect(FriendsWine.TIPSY),
                        "Drinking must not take effect before the vanilla use duration"))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(player.hasEffect(FriendsWine.TIPSY) && player.getEffect(FriendsWine.TIPSY).getDuration() >= (WineItem.DURATION_TICKS - 3),
                            "Completed drinking must start the 3378-tick personal effect");
                    helper.assertTrue(player.getMainHandItem().is(FriendsWine.WINE.get()) && player.getMainHandItem().getCount() == 1
                                    && player.getInventory().countItem(Items.GLASS_BOTTLE) == 1,
                            "A stacked drink must consume one wine and give back one bottle");
                    helper.assertTrue(DollBlockEntity.getActiveDolls(player.level()).stream()
                                    .noneMatch(doll -> doll.getBlockPos().closerThan(player.blockPosition(), 5)),
                            "Drinking must not create an active musical doll");
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertTrue(player.getEffect(FriendsWine.TIPSY).getDuration() < (WineItem.DURATION_TICKS - 11), "Personal duration must really tick down");
                    FriendsWine.WINE.get().use(player.level(), player, InteractionHand.MAIN_HAND);
                })
                .thenIdle(33)
                .thenExecute(() -> {
                    helper.assertTrue(player.getEffect(FriendsWine.TIPSY).getDuration() >= (WineItem.DURATION_TICKS - 3),
                            "Another completed drink must refresh, not stack, the duration");
                    helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE)
                                    && player.getInventory().countItem(Items.GLASS_BOTTLE) == 2,
                            "The last wine must return a bottle in the drinking hand");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MILK_BUCKET));
                    Items.MILK_BUCKET.use(player.level(), player, InteractionHand.MAIN_HAND);
                })
                .thenIdle(33)
                .thenExecute(() -> {
                    helper.assertFalse(player.hasEffect(FriendsWine.TIPSY), "Vanilla milk must cure the effect");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FriendsWine.WINE.get()));
                    FriendsWine.WINE.get().use(player.level(), player, InteractionHand.MAIN_HAND);
                })
                .thenIdle(33)
                .thenExecute(() -> helper.assertTrue(player.hasEffect(FriendsWine.TIPSY), "Wine must work again after milk"))
                .thenIdle(WineItem.DURATION_TICKS + 1)
                .thenExecute(() -> {
                    helper.assertFalse(player.hasEffect(FriendsWine.TIPSY), "The personal effect must naturally expire");
                    player.addEffect(new MobEffectInstance(FriendsWine.TIPSY, WineItem.DURATION_TICKS));
                    helper.getLevel().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
                            "effect clear @s friendswine:tipsy");
                    helper.assertFalse(player.hasEffect(FriendsWine.TIPSY), "The vanilla effect clear command must work");
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_empty", batch = "friendswine-nitwitTradeAndRestock", timeoutTicks = 100)
    public static void nitwitTradeAndRestock(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        ServerPlayer first = WineGameTests.mockServerPlayer(helper);
        first.setGameMode(GameType.SURVIVAL);
        first.setNoGravity(true);
        Vec3 position = helper.absoluteVec(new Vec3(10.5, 3, 10.5));
        first.moveTo(position);
        Villager nitwit = villager(helper, VillagerProfession.NITWIT, false);
        MerchantOffer foreign = new MerchantOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.STONE), 16, 0, 0);
        for (int i = 0; i < 5; i++) foreign.increaseUses();
        nitwit.getOffers().add(foreign);
        long originalTime = helper.getLevel().getDayTime();
        ServerPlayer[] second = new ServerPlayer[1];
        Villager[] restored = new Villager[1];
        MerchantMenu[] open = new MerchantMenu[1];
        helper.startSequence()
                .thenExecute(() -> {
                    for (var profession : List.of(VillagerProfession.NONE, VillagerProfession.FARMER)) {
                        Villager other = villager(helper, profession, false);
                        first.interactOn(other, InteractionHand.MAIN_HAND);
                        helper.assertFalse(other.getOffers().stream().anyMatch(WineGameTests::modProduct),
                                "Ordinary villagers must not receive these trades");
                        first.closeContainer();
                        other.discard();
                    }
                    Villager baby = villager(helper, VillagerProfession.NITWIT, true);
                    first.interactOn(baby, InteractionHand.MAIN_HAND);
                    helper.assertTrue(baby.getOffers().isEmpty(), "Baby nitwits must not trade");
                    baby.discard();
                    first.interactOn(nitwit, InteractionHand.MAIN_HAND);
                    helper.assertTrue(first.containerMenu instanceof MerchantMenu, "Nitwit interaction must open the vanilla menu");
                    helper.assertTrue(nitwit.getOffers().size() == 4 && nitwit.getOffers().stream().filter(WineGameTests::modProduct).count() == 3,
                            "All three fixed products and the unrelated offer must be present");
                    first.closeContainer();
                    first.interactOn(nitwit, InteractionHand.MAIN_HAND);
                    helper.assertTrue(nitwit.getOffers().size() == 4, "Reopening must not duplicate trades");
                    MerchantMenu menu = (MerchantMenu) first.containerMenu;
                    first.getInventory().add(new ItemStack(Items.EMERALD, 32));
                    for (int i = 0; i < 4; i++) first.getInventory().add(new ItemStack(Items.WHEAT, 64));
                    first.getInventory().add(new ItemStack(Items.GLASS_BOTTLE, 32));
                    trade(helper, first, menu, FriendsWine.DOLL_ITEM.get());
                    trade(helper, first, menu, FriendsWine.REMOTE.get());
                    helper.assertTrue(total(first, menu, Items.EMERALD) == 20, "Doll and remote must cost eight and four emeralds");
                    for (int i = 0; i < 16; i++) trade(helper, first, menu, FriendsWine.WINE.get());
                    helper.assertTrue(total(first, menu, Items.WHEAT) == 0 && total(first, menu, Items.GLASS_BOTTLE) == 16
                                    && first.getInventory().countItem(FriendsWine.WINE.get()) == 16,
                            "Sixteen real trades must deduct 256 wheat and sixteen bottles, then deliver sixteen wines");
                    helper.assertTrue(menu.getOffers().get(index(menu, FriendsWine.WINE.get())).isOutOfStock(),
                            "The seventeenth wine must be unavailable");
                    first.getInventory().add(new ItemStack(Items.WHEAT, 64));
                    menu.setSelectionHint(index(menu, FriendsWine.WINE.get()));
                    menu.tryMoveItems(index(menu, FriendsWine.WINE.get()));
                    menu.clicked(2, 0, ClickType.QUICK_MOVE, first);
                    helper.assertTrue(first.getInventory().countItem(FriendsWine.WINE.get()) == 16
                                    && total(first, menu, Items.WHEAT) == 64,
                            "Out-of-stock clicks must not create wine or deduct materials");
                    helper.assertTrue(nitwit.getVillagerData().getProfession() == VillagerProfession.NITWIT
                                    && nitwit.getVillagerData().getLevel() == 1 && nitwit.getVillagerXp() == 0,
                            "Trading must not change the nitwit's profession or level");
                    first.closeContainer();
                    CompoundTag saved = nitwit.saveWithoutId(new CompoundTag());
                    nitwit.discard();
                    restored[0] = EntityType.VILLAGER.create(helper.getLevel());
                    restored[0].load(saved);
                    restored[0].setUUID(UUID.randomUUID());
                    helper.getLevel().addFreshEntity(restored[0]);
                    second[0] = WineGameTests.mockServerPlayer(helper);
                    second[0].setGameMode(GameType.SURVIVAL);
                    second[0].setNoGravity(true);
                    second[0].moveTo(position);
                    second[0].getInventory().add(new ItemStack(Items.WHEAT, 64));
                    second[0].getInventory().add(new ItemStack(Items.GLASS_BOTTLE, 16));
                    second[0].interactOn(restored[0], InteractionHand.MAIN_HAND);
                    open[0] = (MerchantMenu) second[0].containerMenu;
                    helper.assertTrue(open[0].getOffers().get(index(open[0], FriendsWine.WINE.get())).getUses() == 16,
                            "Stock must survive saving and be shared with another player on the same day");
                    open[0].setSelectionHint(index(open[0], FriendsWine.WINE.get()));
                    open[0].tryMoveItems(index(open[0], FriendsWine.WINE.get()));
                    helper.assertTrue(open[0].getSlot(2).getItem().isEmpty(), "Sold-out offers must not produce a menu result");
                    helper.getLevel().setDayTime((Math.floorDiv(originalTime, 24000) + 1) * 24000);
                })
                .thenIdle(21)
                .thenExecute(() -> {
                    helper.assertTrue(open[0].getOffers().get(index(open[0], FriendsWine.WINE.get())).getUses() == 0,
                            "The next day must refill stock without a workstation");
                    helper.assertTrue(open[0].getSlot(2).getItem().is(FriendsWine.WINE.get()),
                            "An already open merchant menu must refresh its result after restocking");
                    helper.assertTrue(restored[0].getOffers().get(0).getUses() == 5,
                            "Daily refill must not reset an unrelated mod's offer");
                    trade(helper, second[0], open[0], FriendsWine.WINE.get());
                    helper.assertTrue(second[0].getInventory().countItem(FriendsWine.WINE.get()) == 1,
                            "The replenished offer must be usable");
                    second[0].closeContainer();
                    second[0].addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 100));
                    second[0].interactOn(restored[0], InteractionHand.MAIN_HAND);
                    MerchantMenu discounted = (MerchantMenu) second[0].containerMenu;
                    MerchantOffer wine = discounted.getOffers().get(index(discounted, FriendsWine.WINE.get()));
                    helper.assertTrue(wine.getCostA().getCount() < 16 && wine.getBaseCostA().getCount() == 16,
                            "Vanilla village hero discounts must preserve the base price");
                    second[0].closeContainer();
                    helper.getLevel().setDayTime(originalTime);
                    restored[0].discard();
                    helper.getLevel().getServer().getPlayerList().remove(first);
                    helper.getLevel().getServer().getPlayerList().remove(second[0]);
                })
                .thenSucceed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "friendswine:doll_empty", batch = "friendswine-nitwitSleep", timeoutTicks = 500)
    public static void nitwitNaturalDayAndSleep(GameTestHelper helper) {
        PartyGuestGameTests.clean(helper);
        helper.onEachTick(() -> PartyGuestGameTests.clean(helper));
        var level = helper.getLevel();
        long originalTime = level.getDayTime(), day = Math.floorDiv(originalTime, 24000);
        int originalSleeping = level.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        boolean originalDaylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(1, level.getServer());
        for (BlockPos floor : BlockPos.betweenClosed(0, 0, 0, 31, 0, 23)) helper.setBlock(floor, Blocks.STONE);
        BlockPos bed = new BlockPos(20, 1, 10);
        helper.setBlock(bed.west(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(bed, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.HEAD));
        ServerPlayer buyer = WineGameTests.mockServerPlayer(helper), sleeper = WineGameTests.mockServerPlayer(helper);
        buyer.setNoGravity(true);
        sleeper.setNoGravity(true);
        buyer.moveTo(helper.absoluteVec(new Vec3(10.5, 1, 10.5)));
        sleeper.moveTo(helper.absoluteVec(new Vec3(20.5, 1, 10.5)));
        Villager nitwit = villager(helper, VillagerProfession.NITWIT, false);
        nitwit.setNoAi(false);
        nitwit.setNoGravity(false);
        MerchantOffer foreign = new MerchantOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.STONE), 16, 0, 0);
        for (int i = 0; i < 7; i++) foreign.increaseUses();
        nitwit.getOffers().add(foreign);
        MerchantMenu[] menu = {null};
        EmbeddedChannel channel = channel(buyer);
        long[] wokeAt = {-1};
        helper.onEachTick(() -> {
            sleeper.doTick();
            if (sleeper.isSleeping()) helper.assertTrue(level.getDayTime() < (day + 2) * 24000,
                    "The sleeper must wake when the native server skips the night");
            if (wokeAt[0] == -1 && level.getDayTime() >= (day + 2) * 24000) wokeAt[0] = level.getGameTime();
        });
        helper.startSequence().thenExecute(() -> {
            level.setDayTime(day * 24000 + 23960);
            menu[0] = buyOut(helper, buyer, nitwit);
            drain(channel);
        }).thenIdle(61).thenExecute(() -> {
            helper.assertTrue(level.getDayTime() >= (day + 1) * 24000, "Natural daylight ticks must cross the day boundary");
            assertRestocked(helper, menu[0], "natural day");
            helper.assertTrue(menu[0].getSlot(2).getItem().is(FriendsWine.WINE.get()), "Natural-day refill refreshes an open result slot");
            helper.assertTrue(drain(channel).stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket),
                    "Restock must send native offer packets to the buyer");
            menu[0] = buyOut(helper, buyer, nitwit);
            level.setDayTime((day + 1) * 24000 + 14000);
        }).thenIdle(2).thenExecute(() -> {
            helper.assertTrue(sleeper.startSleepInBed(helper.absolutePos(bed)).right().isPresent(), "A real bed must allow native player sleep");
            helper.assertTrue(sleeper.isSleeping(), "The player must actually be sleeping");
            drain(channel);
        }).thenWaitUntil(() -> helper.assertTrue(wokeAt[0] >= 0, "Native sleep must advance to the next morning"))
                .thenIdle(20).thenExecute(() -> {
                    helper.assertFalse(sleeper.isSleeping(), "Native sleep skipping must wake the player");
                    helper.assertTrue(level.getGameTime() - wokeAt[0] <= 20, "Check refill within twenty ticks of waking");
                    assertRestocked(helper, menu[0], "native sleep");
                    helper.assertTrue(menu[0].getSlot(2).getItem().is(FriendsWine.WINE.get()), "Sleep refill refreshes an already-open result slot");
                    helper.assertTrue(nitwit.getOffers().get(0).getUses() == 7, "Daily refill must preserve unrelated offers");
                    helper.assertTrue(drain(channel).stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket),
                            "Sleep refill must also send native offer packets");
                    menu[0] = buyOut(helper, buyer, nitwit);
                    buyer.closeContainer();
                    buyer.interactOn(nitwit, InteractionHand.MAIN_HAND);
                    menu[0] = (MerchantMenu) buyer.containerMenu;
                    helper.assertTrue(menu[0].getOffers().stream().filter(WineGameTests::modProduct).allMatch(MerchantOffer::isOutOfStock),
                            "Reopening on the same day must not refill stock");
                    // A time command/mod can rewind the calendar after a legitimate sleep refill.
                    level.setDayTime(day * 24000 + 23960);
                }).thenIdle(21).thenExecute(() -> {
                    helper.assertTrue(menu[0].getOffers().stream().filter(WineGameTests::modProduct).allMatch(MerchantOffer::isOutOfStock),
                            "Rewinding time must not instantly refill stock");
                }).thenIdle(40).thenExecute(() -> {
                    assertRestocked(helper, menu[0], "calendar rewind recovery");
                    helper.assertTrue(nitwit.getOffers().get(0).getUses() == 7, "Calendar recovery must preserve unrelated offers");
                    buyer.closeContainer();
                    nitwit.discard();
                    level.setDayTime(originalTime);
                    level.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(originalSleeping, level.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(originalDaylight, level.getServer());
                    level.getServer().getPlayerList().remove(buyer);
                    level.getServer().getPlayerList().remove(sleeper);
                }).thenSucceed();
    }

    private static MerchantMenu buyOut(GameTestHelper helper, ServerPlayer player, Villager nitwit) {
        player.closeContainer();
        player.getInventory().clearContent();
        for (int i = 0; i < 3; i++) player.getInventory().add(new ItemStack(Items.EMERALD, 64));
        for (int i = 0; i < 4; i++) player.getInventory().add(new ItemStack(Items.WHEAT, 64));
        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE, 16));
        player.interactOn(nitwit, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu instanceof MerchantMenu, "Normal-AI nitwits must open the native trade menu");
        MerchantMenu menu = (MerchantMenu) player.containerMenu;
        for (Item product : new Item[]{FriendsWine.DOLL_ITEM.get(), FriendsWine.REMOTE.get(), FriendsWine.WINE.get()}) {
            for (int i = 0; i < 16; i++) trade(helper, player, menu, product);
            helper.assertTrue(menu.getOffers().get(index(menu, product)).isOutOfStock(), "Sixteen real trades must sell out each product");
        }
        player.getInventory().add(new ItemStack(Items.WHEAT, 16));
        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        menu.setSelectionHint(index(menu, FriendsWine.WINE.get()));
        menu.tryMoveItems(index(menu, FriendsWine.WINE.get()));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Sold-out stock must leave an empty result slot");
        return menu;
    }

    private static void assertRestocked(GameTestHelper helper, MerchantMenu menu, String phase) {
        helper.assertTrue(menu.getOffers().stream().filter(WineGameTests::modProduct).allMatch(offer -> offer.getUses() == 0 && offer.getMaxUses() == 16),
                "All three products must replenish to sixteen uses: " + phase + "; time=" + helper.getLevel().getDayTime()
                        + "; uses=" + menu.getOffers().stream().filter(WineGameTests::modProduct).map(MerchantOffer::getUses).toList());
    }

    private static Villager villager(GameTestHelper helper, VillagerProfession profession, boolean baby) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(10.5, 3, 10.5));
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        villager.setNoAi(true);
        villager.setNoGravity(true);
        if (baby) villager.setAge(-24000);
        return villager;
    }

    private static boolean modProduct(MerchantOffer offer) {
        return offer.getResult().is(FriendsWine.DOLL_ITEM.get()) || offer.getResult().is(FriendsWine.REMOTE.get())
                || offer.getResult().is(FriendsWine.WINE.get());
    }

    private static int index(MerchantMenu menu, Item product) {
        for (int i = 0; i < menu.getOffers().size(); i++) if (menu.getOffers().get(i).getResult().is(product)) return i;
        throw new AssertionError("Missing product offer");
    }

    private static int total(ServerPlayer player, MerchantMenu menu, Item material) {
        int total = player.getInventory().countItem(material);
        for (int i = 0; i < 2; i++) if (menu.getSlot(i).getItem().is(material)) total += menu.getSlot(i).getItem().getCount();
        return total;
    }

    private static void trade(GameTestHelper helper, ServerPlayer player, MerchantMenu menu, Item product) {
        int before = player.getInventory().countItem(product);
        menu.setSelectionHint(index(menu, product));
        menu.tryMoveItems(index(menu, product));
        menu.slotsChanged(player.getInventory());
        helper.assertTrue(menu.getSlot(2).getItem().is(product), "Paid inputs must produce the selected vanilla trade result");
        // A normal click buys one. Shift-click intentionally buys every affordable result in vanilla.
        menu.clicked(2, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(product) && menu.getCarried().getCount() == 1,
                "The vanilla result click must transfer one product to the cursor");
        player.getInventory().add(menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(player.getInventory().countItem(product) == before + 1,
                "The real result-slot transaction must deliver exactly one product");
    }
}
