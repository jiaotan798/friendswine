package com.friendswine;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** 酒沿用有序配方、配方书与网络格式，仅补普通水瓶判定，避免消耗其他药水。 */
public final class WineRecipe implements CraftingRecipe {
    public static final RecipeSerializer<WineRecipe> SERIALIZER = new RecipeSerializer<>() {
        private final ShapedRecipe.Serializer vanilla = new ShapedRecipe.Serializer();
        public WineRecipe fromJson(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonObject json) { return new WineRecipe(vanilla.fromJson(id, json)); }
        public WineRecipe fromNetwork(net.minecraft.resources.ResourceLocation id, net.minecraft.network.FriendlyByteBuf buf) { return new WineRecipe(vanilla.fromNetwork(id, buf)); }
        public void toNetwork(net.minecraft.network.FriendlyByteBuf buf, WineRecipe recipe) { vanilla.toNetwork(buf, recipe.shaped); }
    };
    private final ShapedRecipe shaped;
    private WineRecipe(ShapedRecipe shaped) { this.shaped = shaped; }

    /** 先核对完整的三乘三配方，再校验中央瓶中的原版水与附加效果。 */
    public boolean matches(net.minecraft.world.inventory.CraftingContainer input, Level level) {
        if (!shaped.matches(input, level)) return false;
        ItemStack bottle = input.getItem(4);
        return bottle.is(Items.POTION) && net.minecraft.world.item.alchemy.PotionUtils.getPotion(bottle) == net.minecraft.world.item.alchemy.Potions.WATER
                && net.minecraft.world.item.alchemy.PotionUtils.getCustomEffects(bottle).isEmpty();
    }
    public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer input, net.minecraft.core.RegistryAccess registries) { return shaped.assemble(input, registries); }
    public String getGroup() { return shaped.getGroup(); }
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) { return shaped.getResultItem(registries); }
    public net.minecraft.core.NonNullList<Ingredient> getIngredients() { return shaped.getIngredients(); }
    public boolean canCraftInDimensions(int width, int height) { return shaped.canCraftInDimensions(width, height); }
    public net.minecraft.resources.ResourceLocation getId() { return shaped.getId(); }
    public CraftingBookCategory category() { return shaped.category(); }
    public boolean showNotification() { return shaped.showNotification(); }
    public RecipeSerializer<WineRecipe> getSerializer() { return FriendsWine.WATER_WINE.get(); }
}
