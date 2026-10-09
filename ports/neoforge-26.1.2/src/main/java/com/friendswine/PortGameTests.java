package com.friendswine;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.gametest.framework.*;
import java.util.function.*;
import java.util.*;
public final class PortGameTests {
    private record Test(String name,String structure,int ticks,Consumer<GameTestHelper> run){}
    private static final List<Test> TESTS=List.of(
        new Test("playbackandattraction","doll_range",2400,DollGameTests::playbackAndAttraction),
        new Test("remotestages","doll_empty",1180,DollGameTests::remoteStages),
        new Test("distributedslots","doll_empty",400,DollGameTests::distributedSlots),
        new Test("orbitandrelease","doll_empty",1200,DollGameTests::orbitAndRelease),
        new Test("dollanimationpriority","doll_empty",100,WineGameTests::dollAnimationPriority),
        new Test("observereffectpackets","doll_empty",120,WineGameTests::observerEffectPackets),
        new Test("drinkandcreativetab","doll_empty",3700,WineGameTests::drinkAndCreativeTab),
        new Test("nitwittradeandrestock","doll_empty",100,WineGameTests::nitwitTradeAndRestock),
        new Test("nitwitnaturaldayandsleep","doll_empty",500,WineGameTests::nitwitNaturalDayAndSleep),
        new Test("protectionandrelease","doll_range",130,PeaceGameTests::protectionAndRelease),
        new Test("creeperstillexplodes","doll_range",180,PeaceGameTests::creeperStillExplodes),
        new Test("eggsandnaturalconditions","doll_range",100,PartyGuestGameTests::eggsAndNaturalConditions),
        new Test("stagespawnandcombinedcap","doll_range",100,PartyGuestGameTests::stageSpawnAndCombinedCap),
        new Test("theftguardsandhands","doll_range",100,PartyGuestGameTests::theftGuardsAndHands),
        new Test("carrysaveplacementanddeath","doll_range",100,PartyGuestGameTests::carrySavePlacementAndDeath),
        new Test("realaiholderandidledoll","doll_range",300,PartyGuestGameTests::realAiHolderAndIdleDoll));
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        var functions=net.neoforged.neoforge.registries.DeferredRegister.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION,"friendswine");
        for(var test:TESTS)functions.register(test.name,()->test.run);
        functions.register(bus);
        bus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event)->{
            for(var test:TESTS){
                var id=Identifier.fromNamespaceAndPath("friendswine",test.name);
                var env=event.registerEnvironment(id,new TestEnvironmentDefinition.AllOf(List.of()));
                event.registerTest(id,new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id),new TestData<>(env,Identifier.fromNamespaceAndPath("friendswine",test.structure),test.ticks,0,true)));
            }
        });
    }
}
