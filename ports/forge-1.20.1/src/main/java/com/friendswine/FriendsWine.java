package com.friendswine;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.registries.*;
@Mod(FriendsWine.MODID)
public final class FriendsWine {
    public static final String MODID="friendswine";
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,MODID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,MODID);
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,MODID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MODID);
    private static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MODID);
    public static final RegistryObject<DollBlock> DOLL=BLOCKS.register("doll",()->new DollBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<BlockItem> DOLL_ITEM=ITEMS.register("doll",()->new BlockItem(DOLL.get(),new Item.Properties()) {
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
                private final com.friendswine.client.DollItemRenderer renderer=new com.friendswine.client.DollItemRenderer();
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
            });
        }
    });
    public static final RegistryObject<RemoteItem> REMOTE=ITEMS.register("remote",()->new RemoteItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<WineItem> WINE=ITEMS.register("wine",()->new WineItem(new Item.Properties().stacksTo(16)));
    private static final RegistryObject<MobEffect> EFFECT=EFFECTS.register("tipsy",()->new MobEffect(MobEffectCategory.BENEFICIAL,0xDFA33B) {});
    public static MobEffect tipsy() { return EFFECT.get(); }
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final RegistryObject<EntityType<PartyGuestEntity>> KASUMI = ENTITIES.register("kasumi", () -> EntityType.Builder.<PartyGuestEntity>of(PartyGuestEntity::new, MobCategory.CREATURE).sized(0.6F, 1.0F).clientTrackingRange(10).build(MODID + ":kasumi"));
    public static final RegistryObject<EntityType<PartyGuestEntity>> EMMA = ENTITIES.register("emma", () -> EntityType.Builder.<PartyGuestEntity>of(PartyGuestEntity::new, MobCategory.CREATURE).sized(0.6F, 1.0F).clientTrackingRange(10).build(MODID + ":emma"));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> KASUMI_SPAWN_EGG = ITEMS.register("kasumi_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(KASUMI, 0x70543B, 0xE99334, new Item.Properties()));
    public static final RegistryObject<net.minecraftforge.common.ForgeSpawnEggItem> EMMA_SPAWN_EGG = ITEMS.register("emma_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(EMMA, 0xECE7F5, 0xE67DAB, new Item.Properties()));
    public static final RegistryObject<CreativeModeTab> TAB=TABS.register("main",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.friendswine")).icon(()->new ItemStack(DOLL_ITEM.get())).displayItems((params,out)->{out.accept(DOLL_ITEM.get());out.accept(REMOTE.get());out.accept(WINE.get());out.accept(KASUMI_SPAWN_EGG.get());out.accept(EMMA_SPAWN_EGG.get());}).build());
    public static final RegistryObject<BlockEntityType<DollBlockEntity>> DOLL_BLOCK_ENTITY=BES.register("doll",()->BlockEntityType.Builder.of(DollBlockEntity::new,DOLL.get()).build(null));
    public FriendsWine() {
        var bus=FMLJavaModLoadingContext.get().getModEventBus(); BLOCKS.register(bus); ITEMS.register(bus); EFFECTS.register(bus); TABS.register(bus); BES.register(bus); ENTITIES.register(bus);
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent event) -> { event.put(KASUMI.get(), PartyGuestEntity.createAttributes().build()); event.put(EMMA.get(), PartyGuestEntity.createAttributes().build()); });
        bus.addListener((net.minecraftforge.event.entity.SpawnPlacementRegisterEvent event) -> { event.register(KASUMI.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PartyGuestEntity::checkNaturalSpawn, net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE); event.register(EMMA.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PartyGuestEntity::checkNaturalSpawn, net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE); });
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,ClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,ServerConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.LevelTickEvent e)->{if(e.phase==TickEvent.Phase.END && e.level instanceof net.minecraft.server.level.ServerLevel level){DollAttraction.tick(level);level.players().forEach(TipsySync::tick);}});
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload e)->{if(e.getLevel() instanceof net.minecraft.world.level.Level level){DollBlockEntity.forgetLevel(level);DollAttraction.releaseLevel(level);}});
        MinecraftForge.EVENT_BUS.addListener((LivingChangeTargetEvent e)->{if(e.getEntity() instanceof net.minecraft.world.entity.Mob mob && !(mob instanceof net.minecraft.world.entity.monster.Creeper) && e.getNewTarget()!=null && DollPeace.protectedCombat(mob,e.getNewTarget()))e.setCanceled(true);});
        MinecraftForge.EVENT_BUS.addListener((LivingAttackEvent e)->{if(DollPeace.cancelDamage(e.getSource(),e.getEntity()))e.setCanceled(true);});
        MinecraftForge.EVENT_BUS.addListener((LivingEvent.LivingTickEvent e)->{if(e.getEntity() instanceof net.minecraft.world.entity.Mob mob)DollPeace.tick(mob);if(e.getEntity() instanceof net.minecraft.world.entity.npc.Villager villager)NitwitTrades.tick(villager);});
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e)->{if(e.getTarget() instanceof net.minecraft.world.entity.npc.Villager villager)NitwitTrades.interact(villager,e.getEntity(),e.getHand());});
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)TipsySync.startTracking(player,e.getTarget());});
        SettingsNetworking.register();
        if(net.minecraftforge.fml.loading.FMLEnvironment.dist==net.minecraftforge.api.distmarker.Dist.CLIENT)com.friendswine.client.FriendsWineClient.register();
    }
}
