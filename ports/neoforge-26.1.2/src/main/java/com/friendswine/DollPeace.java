package com.friendswine;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Protect either end of mob combat, including an attacker outside the doll's radius. */
final class DollPeace {
    private DollPeace() {}

    static boolean protectedCombat(Mob attacker, LivingEntity victim) {
        if (attacker.level().isClientSide() || attacker.level() != victim.level()
                || !(victim instanceof Mob || victim instanceof Player)) return false;
        return DollBlockEntity.nearestActive(attacker.level(), attacker.position(), DollAttraction.SEEK_RADIUS) != null
                || DollBlockEntity.nearestActive(victim.level(), victim.position(), DollAttraction.SEEK_RADIUS) != null;
    }

    static void target(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && !(mob instanceof Creeper)
                && event.getNewAboutToBeSetTarget() != null && protectedCombat(mob, event.getNewAboutToBeSetTarget())) {
            // Cancel acquisition; clear any previously stored target in the pre-tick hook below.
            event.setCanceled(true);
        }
    }

    static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob mob)) return;
        if (mob instanceof Creeper && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) return;
        if (protectedCombat(mob, event.getEntity())) event.setCanceled(true);
    }

    static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || mob instanceof Creeper) return;
        if (mob.getTarget() != null && protectedCombat(mob, mob.getTarget())) mob.setTarget(null);
        var brain = mob.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && protectedCombat(mob, brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElseThrow())) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
}
