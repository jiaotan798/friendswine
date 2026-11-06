package com.friendswine;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** 酒沿用有序配方、配方书与网络格式，仅补普通水瓶判定，避免消耗其他药水。 */
public final class WineRecipe implements CraftingRecipe {
    public static final RecipeSerializer<WineRecipe> SERIALIZER = new RecipeSerializer<>() {
        public com.mojang.serialization.MapCodec<WineRecipe> codec() { return ShapedRecipe.Serializer.CODEC.xmap(WineRecipe::new, recipe -> recipe.shaped); }
        public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, WineRecipe> streamCodec() { return ShapedRecipe.Serializer.STREAM_CODEC.map(WineRecipe::new, recipe -> recipe.shaped); }
    };
    private final ShapedRecipe shaped;
    private WineRecipe(ShapedRecipe shaped) { this.shaped = shaped; }

    /** 先核对完整的三乘三配方，再校验中央瓶中的原版水与附加效果。 */
    public boolean matches(CraftingInput input, Level level) {
        if (!shaped.matches(input, level)) return false;
        ItemStack bottle = input.getItem(4);
        return bottle.is(Items.POTION) && bottle.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                net.minecraft.world.item.alchemy.PotionContents.EMPTY).is(net.minecraft.world.item.alchemy.Potions.WATER);
    }
    public ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) { return shaped.assemble(input, registries); }
    public String getGroup() { return shaped.getGroup(); }
    public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registries) { return shaped.getResultItem(registries); }
    public net.minecraft.core.NonNullList<Ingredient> getIngredients() { return shaped.getIngredients(); }
    public boolean canCraftInDimensions(int width, int height) { return shaped.canCraftInDimensions(width, height); }
    public CraftingBookCategory category() { return shaped.category(); }
    public boolean showNotification() { return shaped.showNotification(); }
    public RecipeSerializer<WineRecipe> getSerializer() { return FriendsWine.WATER_WINE.get(); }
}
