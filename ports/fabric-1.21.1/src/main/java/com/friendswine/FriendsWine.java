package com.friendswine;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
        Registry.register(registry, ResourceLocation.fromNamespaceAndPath(MODID,id),value);
        return () -> value;
    }
    public static final Supplier<DollBlock> DOLL = register(BuiltInRegistries.BLOCK,"doll",new DollBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.WOOL).noOcclusion()));
    public static final Supplier<BlockItem> DOLL_ITEM = register(BuiltInRegistries.ITEM,"doll",new BlockItem(DOLL.get(),new Item.Properties()));
    public static final Supplier<RemoteItem> REMOTE = register(BuiltInRegistries.ITEM,"remote",new RemoteItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<WineItem> WINE = register(BuiltInRegistries.ITEM,"wine",new WineItem(new Item.Properties().stacksTo(16)));
    public static final Holder.Reference<MobEffect> TIPSY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,ResourceLocation.fromNamespaceAndPath(MODID,"tipsy"),new MobEffect(MobEffectCategory.BENEFICIAL,0xDFA33B) {});
    public static final Supplier<BlockEntityType<DollBlockEntity>> DOLL_BLOCK_ENTITY = register(BuiltInRegistries.BLOCK_ENTITY_TYPE,"doll",BlockEntityType.Builder.of(DollBlockEntity::new,DOLL.get()).build(null));
    private static EntityType<PartyGuestEntity> guestType() {
        return net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder.<PartyGuestEntity>createMob().entityFactory(PartyGuestEntity::new).spawnGroup(MobCategory.CREATURE).dimensions(net.minecraft.world.entity.EntityDimensions.scalable(0.6F, 1.0F)).defaultAttributes(PartyGuestEntity::createAttributes).spawnRestriction(net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PartyGuestEntity::checkNaturalSpawn).build();
    }
    public static final Supplier<EntityType<PartyGuestEntity>> KASUMI = register(BuiltInRegistries.ENTITY_TYPE, "kasumi", guestType());
    public static final Supplier<EntityType<PartyGuestEntity>> EMMA = register(BuiltInRegistries.ENTITY_TYPE, "emma", guestType());
    public static final Supplier<SpawnEggItem> KASUMI_SPAWN_EGG = register(BuiltInRegistries.ITEM, "kasumi_spawn_egg", new SpawnEggItem(KASUMI.get(), 0x70543B, 0xE99334, new Item.Properties()));
    public static final Supplier<SpawnEggItem> EMMA_SPAWN_EGG = register(BuiltInRegistries.ITEM, "emma_spawn_egg", new SpawnEggItem(EMMA.get(), 0xECE7F5, 0xE67DAB, new Item.Properties()));
    public static final Supplier<CreativeModeTab> TAB = register(BuiltInRegistries.CREATIVE_MODE_TAB,"main",FabricItemGroup.builder().title(Component.translatable("itemGroup.friendswine")).icon(() -> new ItemStack(DOLL_ITEM.get())).displayItems((parameters,output) -> {
        output.accept(DOLL_ITEM.get());output.accept(REMOTE.get());output.accept(WINE.get());output.accept(KASUMI_SPAWN_EGG.get());output.accept(EMMA_SPAWN_EGG.get());
    }).build());
    @Override public void onInitialize() {
        ServerConfig.load();
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(), MobCategory.CREATURE, KASUMI.get(), 1, 1, 1);
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(), MobCategory.CREATURE, EMMA.get(), 1, 1, 1);
        ServerTickEvents.END_WORLD_TICK.register(level -> { DollAttraction.tick(level); level.players().forEach(TipsySync::tick); });
        ServerWorldEvents.UNLOAD.register((server,level) -> { DollBlockEntity.forgetLevel(level); DollAttraction.releaseLevel(level); });
        EntityTrackingEvents.START_TRACKING.register((entity,player) -> TipsySync.startTracking(player,entity));
        SettingsNetworking.register();
    }
}
