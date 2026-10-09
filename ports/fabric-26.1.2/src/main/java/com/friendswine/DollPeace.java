package com.friendswine;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

/** Protect either end of mob combat, including an attacker outside the doll's radius. */
public final class DollPeace {
    private DollPeace() {}

    public static boolean protectedCombat(Mob attacker, LivingEntity victim) {
        if (attacker.level().isClientSide() || attacker.level() != victim.level()
                || !(victim instanceof Mob || victim instanceof Player)) return false;
        return DollBlockEntity.nearestActive(attacker.level(), attacker.position(), DollAttraction.SEEK_RADIUS) != null
                || DollBlockEntity.nearestActive(victim.level(), victim.position(), DollAttraction.SEEK_RADIUS) != null;
    }

    public static boolean cancelDamage(net.minecraft.world.damagesource.DamageSource source, LivingEntity victim) {
        if (!(source.getEntity() instanceof Mob mob)) return false;
        if (mob instanceof Creeper && source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        return protectedCombat(mob, victim);
    }
    public static void tick(Mob mob) {
        if (mob.level().isClientSide() || mob instanceof Creeper) return;
        if (mob.getTarget() != null && protectedCombat(mob, mob.getTarget())) mob.setTarget(null);
        var brain = mob.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && protectedCombat(mob, brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElseThrow())) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
}
