package com.friendswine;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** 酒沿用有序配方、配方书与网络格式，仅补普通水瓶判定，避免消耗其他药水。 */
public final class WineRecipe implements CraftingRecipe {
    public static final RecipeSerializer<WineRecipe> SERIALIZER = new RecipeSerializer<>(ShapedRecipe.MAP_CODEC.xmap(WineRecipe::new, recipe -> recipe.shaped), ShapedRecipe.STREAM_CODEC.map(WineRecipe::new, recipe -> recipe.shaped));
    private final ShapedRecipe shaped;
    private WineRecipe(ShapedRecipe shaped) { this.shaped = shaped; }

    /** 先核对完整的三乘三配方，再校验中央瓶中的原版水与附加效果。 */
    public boolean matches(CraftingInput input, Level level) {
        if (!shaped.matches(input, level)) return false;
        ItemStack bottle = input.getItem(4);
        return bottle.is(Items.POTION) && bottle.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                net.minecraft.world.item.alchemy.PotionContents.EMPTY).is(net.minecraft.world.item.alchemy.Potions.WATER);
    }
    public ItemStack assemble(CraftingInput input) { return shaped.assemble(input); }
    public String group() { return shaped.group(); }
    public PlacementInfo placementInfo() { return shaped.placementInfo(); }
    public java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() { return shaped.display(); }
    public CraftingBookCategory category() { return shaped.category(); }
    public boolean showNotification() { return shaped.showNotification(); }
    public RecipeSerializer<WineRecipe> getSerializer() { return FriendsWine.WATER_WINE.get(); }
}
