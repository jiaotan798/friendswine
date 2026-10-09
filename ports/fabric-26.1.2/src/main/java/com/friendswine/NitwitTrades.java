package com.friendswine;

import java.util.Optional;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

public final class NitwitTrades {
    private static final String RESTOCK_DAY = "FriendsWineRestockDay";
    private static final int STOCK = 16;

    private NitwitTrades() {}

    public static void interact(Villager villager, Player player, net.minecraft.world.InteractionHand hand) {
        if (eligible(villager)
                && !villager.isTrading() && !villager.isSleeping() && !player.isSecondaryUseActive()
                && !player.getItemInHand(hand).is(Items.VILLAGER_SPAWN_EGG)) {
            ensureOffers(villager);
            restock(villager);
            // Do not cancel: vanilla mobInteract opens the normal merchant menu with these offers.
        }
    }

    public static void tick(Villager villager) {
        if (eligible(villager)
                && villager.tickCount % 20 == 0 && ((NitwitData) villager).friendswine$data().getLong(RESTOCK_DAY).isPresent()) {
            restock(villager);
        }
    }

    private static boolean eligible(Villager villager) {
        return !villager.level().isClientSide() && villager.isAlive() && !villager.isBaby()
                && villager.getVillagerData().profession().is(VillagerProfession.NITWIT);
    }

    private static MerchantOffer offer(Item product) {
        ItemCost cost = new ItemCost(product == FriendsWine.WINE.get() ? Items.WHEAT : Items.EMERALD,
                product == FriendsWine.WINE.get() ? 16 : product == FriendsWine.DOLL_ITEM.get() ? 8 : 4);
        Optional<ItemCost> bottle = product == FriendsWine.WINE.get()
                ? Optional.of(new ItemCost(Items.GLASS_BOTTLE)) : Optional.empty();
        return new MerchantOffer(cost, bottle, new ItemStack(product), STOCK, 0, 0);
    }

    private static boolean sameProductOffer(MerchantOffer offer, MerchantOffer expected) {
        return ItemStack.matches(offer.getResult(), expected.getResult())
                && ItemStack.matches(offer.getBaseCostA(), expected.getBaseCostA())
                && ItemStack.matches(offer.getCostB(), expected.getCostB())
                && offer.getMaxUses() == STOCK && offer.getXp() == 0 && offer.getPriceMultiplier() == 0;
    }

    private static void ensureOffers(Villager villager) {
        for (Item product : new Item[]{FriendsWine.DOLL_ITEM.get(), FriendsWine.REMOTE.get(), FriendsWine.WINE.get()}) {
            MerchantOffer expected = offer(product);
            if (villager.getOffers().stream().noneMatch(existing -> sameProductOffer(existing, expected))) {
                villager.getOffers().add(expected);
            }
        }
        if (!((NitwitData) villager).friendswine$data().getLong(RESTOCK_DAY).isPresent()) {
            ((NitwitData) villager).friendswine$data().putLong(RESTOCK_DAY, Math.floorDiv(villager.level().getOverworldClockTime(), 24000));
        }
    }

    private static void restock(Villager villager) {
        long day = Math.floorDiv(villager.level().getOverworldClockTime(), 24000);
        long previousDay = ((NitwitData) villager).friendswine$data().getLongOr(RESTOCK_DAY, 0);
        if (day < previousDay) {
            // Rebase a rewound calendar without refilling; its next new day can restock normally.
            ((NitwitData) villager).friendswine$data().putLong(RESTOCK_DAY, day);
            return;
        }
        if (day == previousDay) return;
        for (Item product : new Item[]{FriendsWine.DOLL_ITEM.get(), FriendsWine.REMOTE.get(), FriendsWine.WINE.get()}) {
            MerchantOffer expected = offer(product);
            villager.getOffers().stream().filter(existing -> sameProductOffer(existing, expected))
                    .forEach(MerchantOffer::resetUses);
        }
        ((NitwitData) villager).friendswine$data().putLong(RESTOCK_DAY, day);
        Player buyer = villager.getTradingPlayer();
        if (buyer != null && buyer.containerMenu instanceof MerchantMenu menu) {
            menu.slotsChanged(buyer.getInventory());
            buyer.sendMerchantOffers(menu.containerId, villager.getOffers(), villager.getVillagerData().level(),
                    villager.getVillagerXp(), villager.showProgressBar(), villager.canRestock());
            menu.broadcastChanges();
        }
    }
}
