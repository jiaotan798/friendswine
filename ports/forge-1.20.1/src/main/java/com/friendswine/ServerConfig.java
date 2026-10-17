package com.friendswine;

import net.minecraftforge.common.ForgeConfigSpec;

/** 服务端统一控制环绕速度和死亡效果接收范围，客户端不能自行扩大作用范围。 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<Number> ORBIT_SPEED;

    public static final ForgeConfigSpec.EnumValue<DeathFlashScope> DEATH_FLASH_SCOPE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ORBIT_SPEED = builder.comment("Counterclockwise orbit speed multiplier. 1 = 20 seconds per turn; 0 pauses orbit.")
                .translation("friendswine.config.orbitSpeed").<Number>define("orbitSpeed", 1.0,
                        value -> value instanceof Number n && Double.isFinite(n.doubleValue())
                                && n.doubleValue() >= 0 && n.doubleValue() <= 4);
        DEATH_FLASH_SCOPE = builder.comment("死亡效果范围：NEARBY 为同维度 48 格，GLOBAL 为全部维度；图片、音效和发言统一受此设置控制。")
                .translation("friendswine.config.deathFlashScope")
                .defineEnum("deathFlashScope", DeathFlashScope.NEARBY);
        SPEC = builder.build();
    }

    public static DeathFlashScope deathFlashScope() { return DEATH_FLASH_SCOPE.get(); }

    private ServerConfig() {}
    public static void save(double speed) { save(speed, deathFlashScope()); }
    /** 两个字段作为一次修改保存；失败时恢复原值，旧环绕速度命令保持范围选项。 */
    public static void save(double speed, DeathFlashScope scope) {
        if (!Double.isFinite(speed) || speed < 0 || speed > 4 || scope == null) throw new IllegalArgumentException("Invalid server settings");
        Number previousSpeed = ORBIT_SPEED.get();
        DeathFlashScope previousScope = DEATH_FLASH_SCOPE.get();
        try { ORBIT_SPEED.set(speed); DEATH_FLASH_SCOPE.set(scope); SPEC.save(); }
        catch (RuntimeException failure) { ORBIT_SPEED.set(previousSpeed); DEATH_FLASH_SCOPE.set(previousScope); throw failure; }
    }

    public static void main(String[] args) {
        var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        SPEC.correct(config);
        assert ORBIT_SPEED.getDefault().doubleValue() == 1 && SPEC.isCorrect(config);
        for (double valid : new double[]{0, 0.5, 2, 4}) { config.set("orbitSpeed", valid); assert SPEC.isCorrect(config); }
        for (Object invalid : new Object[]{-1, 4.01, Double.NaN, Double.POSITIVE_INFINITY, "fast"}) {
            config.set("orbitSpeed", invalid);
            assert !SPEC.isCorrect(config);
            SPEC.correct(config);
            assert SPEC.isCorrect(config) && ((Number) config.get("orbitSpeed")).doubleValue() == 1;
        }
        assert DEATH_FLASH_SCOPE.getDefault() == DeathFlashScope.NEARBY;
        for (var scope : DeathFlashScope.values()) {
            config.set("deathFlashScope", scope.name());
            assert SPEC.isCorrect(config);
        }
        for (Object invalid : new Object[]{"unknown", "", 3}) {
            config.set("deathFlashScope", invalid);
            assert !SPEC.isCorrect(config);
            SPEC.correct(config);
            assert SPEC.isCorrect(config) && config.get("deathFlashScope").toString().equals("NEARBY");
        }
        System.out.println("PASS server orbit values and death flash enum defaults/correction");
    }
}
