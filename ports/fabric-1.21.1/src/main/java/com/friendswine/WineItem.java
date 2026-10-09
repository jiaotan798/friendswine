package com.friendswine;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;

/** Vanilla drinking, stats and bottle handling, plus one visual-only personal effect. */
public final class WineItem extends PotionItem {
    public static final int DURATION_TICKS = 3378;
    public WineItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity drinker) {
        ItemStack result = super.finishUsingItem(stack, level, drinker);
        if (!level.isClientSide && drinker instanceof Player) {
            // Replace, rather than hide behind a longer/infinite instance or stack durations.
            drinker.removeEffect(FriendsWine.TIPSY);
            drinker.addEffect(new MobEffectInstance(FriendsWine.TIPSY,
                    DURATION_TICKS, 0, false, false, true));
        }
        return result;
    }
}
