package com.friendswine;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
public final class FriendsWine implements ModInitializer {
    public static final String MODID = "friendswine";
    private static <V,T extends V> Supplier<T> register(Registry<V> registry, String id, T value) {
        Registry.register(registry, Identifier.fromNamespaceAndPath(MODID,id),value);
        return () -> value;
    }
    public static final Supplier<DollBlock> DOLL = register(BuiltInRegistries.BLOCK,"doll",new DollBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(MODID,"doll"))).mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.WOOL).noOcclusion()));
    public static final Supplier<BlockItem> DOLL_ITEM = register(BuiltInRegistries.ITEM,"doll",new BlockItem(DOLL.get(),new Item.Properties().setId(ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath(MODID,"doll"))).useBlockDescriptionPrefix()));
    public static final Supplier<RemoteItem> REMOTE = register(BuiltInRegistries.ITEM,"remote",new RemoteItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath(MODID,"remote"))).stacksTo(1)));
    public static final Supplier<WineItem> WINE = register(BuiltInRegistries.ITEM,"wine",new WineItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath(MODID,"wine"))).stacksTo(16).component(net.minecraft.core.component.DataComponents.CONSUMABLE,net.minecraft.world.item.component.Consumables.DEFAULT_DRINK).usingConvertsTo(Items.GLASS_BOTTLE)));
    public static final Holder.Reference<MobEffect> TIPSY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,Identifier.fromNamespaceAndPath(MODID,"tipsy"),new MobEffect(MobEffectCategory.BENEFICIAL,0xDFA33B) {});
    public static final Supplier<BlockEntityType<DollBlockEntity>> DOLL_BLOCK_ENTITY = register(BuiltInRegistries.BLOCK_ENTITY_TYPE,"doll",net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(DollBlockEntity::new,DOLL.get()).build());
    private static EntityType<PartyGuestEntity> guestType(String id) {
        return net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType.Builder.createMob(PartyGuestEntity::new, MobCategory.CREATURE, builder -> builder.defaultAttributes(PartyGuestEntity::createAttributes).spawnPlacement(net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PartyGuestEntity::checkNaturalSpawn)).sized(0.6F, 1.0F).clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, id)));
    }
    public static final Supplier<EntityType<PartyGuestEntity>> KASUMI = register(BuiltInRegistries.ENTITY_TYPE, "kasumi", guestType("kasumi"));
    public static final Supplier<EntityType<PartyGuestEntity>> EMMA = register(BuiltInRegistries.ENTITY_TYPE, "emma", guestType("emma"));
    public static final Supplier<SpawnEggItem> KASUMI_SPAWN_EGG = register(BuiltInRegistries.ITEM, "kasumi_spawn_egg", new SpawnEggItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "kasumi_spawn_egg"))).spawnEgg(KASUMI.get())));
    public static final Supplier<SpawnEggItem> EMMA_SPAWN_EGG = register(BuiltInRegistries.ITEM, "emma_spawn_egg", new SpawnEggItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "emma_spawn_egg"))).spawnEgg(EMMA.get())));
    public static final Supplier<CreativeModeTab> TAB = register(BuiltInRegistries.CREATIVE_MODE_TAB,"main",FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.friendswine")).icon(() -> new ItemStack(DOLL_ITEM.get())).displayItems((parameters,output) -> {
        output.accept(DOLL_ITEM.get());output.accept(REMOTE.get());output.accept(WINE.get());output.accept(KASUMI_SPAWN_EGG.get());output.accept(EMMA_SPAWN_EGG.get());
    }).build());
    @Override public void onInitialize() {
        ServerConfig.load();
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(), MobCategory.CREATURE, KASUMI.get(), 1, 1, 1);
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(), MobCategory.CREATURE, EMMA.get(), 1, 1, 1);
        ServerTickEvents.END_LEVEL_TICK.register(level -> { DollAttraction.tick(level); level.players().forEach(TipsySync::tick); });
        ServerLevelEvents.UNLOAD.register((server,level) -> { DollBlockEntity.forgetLevel(level); DollAttraction.releaseLevel(level); });
        EntityTrackingEvents.START_TRACKING.register((entity,player) -> TipsySync.startTracking(player,entity));
        SettingsNetworking.register();
    }
}
