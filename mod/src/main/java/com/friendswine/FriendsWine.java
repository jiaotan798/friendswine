package com.friendswine;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(FriendsWine.MODID)
public final class FriendsWine {
    public static final String MODID = "friendswine";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<DollBlock> DOLL = BLOCKS.register("doll", () ->
            new DollBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(0.5F).sound(SoundType.WOOL).noOcclusion()));
    public static final DeferredItem<BlockItem> DOLL_ITEM = ITEMS.registerSimpleBlockItem("doll", DOLL);
    public static final DeferredItem<RemoteItem> REMOTE = ITEMS.register("remote", () ->
            new RemoteItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<WineItem> WINE = ITEMS.register("wine", () ->
            new WineItem(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<MobEffect, MobEffect> TIPSY = EFFECTS.register("tipsy", () ->
            new MobEffect(MobEffectCategory.BENEFICIAL, 0xDFA33B) {});
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<PartyGuestEntity>> KASUMI = ENTITIES.register("kasumi", () -> EntityType.Builder.<PartyGuestEntity>of(PartyGuestEntity::new, MobCategory.CREATURE).sized(0.6F, 1.0F).clientTrackingRange(10).build(MODID + ":kasumi"));
    public static final DeferredHolder<EntityType<?>, EntityType<PartyGuestEntity>> EMMA = ENTITIES.register("emma", () -> EntityType.Builder.<PartyGuestEntity>of(PartyGuestEntity::new, MobCategory.CREATURE).sized(0.6F, 1.0F).clientTrackingRange(10).build(MODID + ":emma"));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> KASUMI_SPAWN_EGG = ITEMS.register("kasumi_spawn_egg", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(KASUMI, 0x70543B, 0xE99334, new Item.Properties()));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> EMMA_SPAWN_EGG = ITEMS.register("emma_spawn_egg", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(EMMA, 0xECE7F5, 0xE67DAB, new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.friendswine"))
                    .icon(() -> new ItemStack(DOLL_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(DOLL_ITEM);
                        output.accept(REMOTE);
                        output.accept(WINE);
                        output.accept(KASUMI_SPAWN_EGG);
                        output.accept(EMMA_SPAWN_EGG);
                    }).build());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DollBlockEntity>> DOLL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("doll", () -> BlockEntityType.Builder.of(DollBlockEntity::new, DOLL.get()).build(null));

    public FriendsWine(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        TABS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) -> { event.put(KASUMI.get(), PartyGuestEntity.createAttributes().build()); event.put(EMMA.get(), PartyGuestEntity.createAttributes().build()); });
        modBus.addListener((net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) -> { event.register(KASUMI.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PartyGuestEntity::checkNaturalSpawn, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE); event.register(EMMA.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PartyGuestEntity::checkNaturalSpawn, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE); });
        NeoForge.EVENT_BUS.addListener(DollAttraction::tick);
        NeoForge.EVENT_BUS.addListener(DollPeace::target);
        NeoForge.EVENT_BUS.addListener(DollPeace::damage);
        NeoForge.EVENT_BUS.addListener(DollPeace::tick);
        NeoForge.EVENT_BUS.addListener(FriendsWine::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(NitwitTrades::interact);
        NeoForge.EVENT_BUS.addListener(NitwitTrades::tick);
        NeoForge.EVENT_BUS.addListener(TipsySync::tick);
        NeoForge.EVENT_BUS.addListener(TipsySync::startTracking);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            DollBlockEntity.forgetLevel(level);
            if (!level.isClientSide) {
                DollAttraction.releaseLevel(level);
            }
        }
    }
}
