package com.friendswine;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Physical movement is controlled by the server; all clients share the same orbit speed. */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<Number> ORBIT_SPEED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ORBIT_SPEED = builder.comment("Counterclockwise orbit speed multiplier. 1 = 20 seconds per turn; 0 pauses orbit.")
                .translation("friendswine.config.orbitSpeed").<Number>define("orbitSpeed", 1.0,
                        value -> value instanceof Number n && Double.isFinite(n.doubleValue())
                                && n.doubleValue() >= 0 && n.doubleValue() <= 4);
        SPEC = builder.build();
    }

    private ServerConfig() {}

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
        System.out.println("Server orbit config defaults, bounds and invalid value correction passed.");
    }
}
