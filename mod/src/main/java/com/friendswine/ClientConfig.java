package com.friendswine;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local display preferences; server gameplay and synchronized clocks do not use these values. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue COMPRESSION;
    public static final ModConfigSpec.IntValue WIDTH;
    public static final ModConfigSpec.ConfigValue<Number> ROTATION_SPEED;
    public static final ModConfigSpec.ConfigValue<Number> KASUMI_MIN_SCALE;
    public static final ModConfigSpec.ConfigValue<Number> KASUMI_MAX_SCALE;
    public static final ModConfigSpec.ConfigValue<Number> EMMA_MIN_SCALE;
    public static final ModConfigSpec.ConfigValue<Number> EMMA_MAX_SCALE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        COMPRESSION = builder.comment("Vertical compression percent. Feet remain fixed; 0 disables squash.")
                .translation("friendswine.config.compressionPercent").defineInRange("compressionPercent", 50, 0, 90);
        WIDTH = builder.comment("Extra width at maximum compression, in percent. Depth stays unchanged.")
                .translation("friendswine.config.widthPercent").defineInRange("widthPercent", 50, 0, 200);
        ROTATION_SPEED = builder.comment("Rotation speed multiplier for full doll effects; 0 disables rotation.")
                .translation("friendswine.config.rotationSpeed").<Number>define("rotationSpeed", 1.0,
                        value -> value instanceof Number n && Double.isFinite(n.doubleValue())
                                && n.doubleValue() >= 0 && n.doubleValue() <= 4);
        KASUMI_MIN_SCALE = scale(builder,"kasumiMinScale",0.75);
        KASUMI_MAX_SCALE = scale(builder,"kasumiMaxScale",1.25);
        EMMA_MIN_SCALE = scale(builder,"emmaMinScale",0.75);
        EMMA_MAX_SCALE = scale(builder,"emmaMaxScale",1.25);
        // Native numeric Range.correct leaves NaN untouched; finite-value validators reset invalid values.
        SPEC = builder.build();
    }
    private static ModConfigSpec.ConfigValue<Number> scale(ModConfigSpec.Builder builder,String key,double initial) {
        return builder.comment("Local creature display scale, 0.5 to 20. Reversed min/max pairs reset to 0.75 / 1.25.")
                .translation("friendswine.config."+key).<Number>define(key,initial,
                        value -> value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue()>=0.5 && n.doubleValue()<=20);
    }
    private ClientConfig() {}
    private static double creatureScale(boolean emma,boolean maximum) {
        var min=emma ? EMMA_MIN_SCALE : KASUMI_MIN_SCALE;
        var max=emma ? EMMA_MAX_SCALE : KASUMI_MAX_SCALE;
        double minimum=min.get().doubleValue(), limit=max.get().doubleValue();
        // Native config screens edit entries independently. Enforce pair order on every load/reload read.
        if (!validPair(minimum,limit)) {
            min.set(0.75); max.set(1.25); SPEC.save();
            minimum=0.75; limit=1.25;
        }
        return maximum ? limit : minimum;
    }
    public static double creatureMin(boolean emma) { return creatureScale(emma,false); }
    public static double creatureMax(boolean emma) { return creatureScale(emma,true); }
    public static void save(int compression,int width,double rotation) {
        save(compression,width,rotation,creatureMin(false),creatureMax(false),creatureMin(true),creatureMax(true));
    }
    public static void save(int compression,int width,double rotation,double kasumiMin,double kasumiMax,double emmaMin,double emmaMax) {
        if(compression<0||compression>90||width<0||width>200||!Double.isFinite(rotation)||rotation<0||rotation>4) throw new IllegalArgumentException("Invalid client settings");
        requireCreatureScales(kasumiMin,kasumiMax,emmaMin,emmaMax);
        COMPRESSION.set(compression); WIDTH.set(width); ROTATION_SPEED.set(rotation);
        KASUMI_MIN_SCALE.set(kasumiMin); KASUMI_MAX_SCALE.set(kasumiMax); EMMA_MIN_SCALE.set(emmaMin); EMMA_MAX_SCALE.set(emmaMax);
        SPEC.save();
    }
    public static float heightScale(float squash) { return JellyAnimation.heightScale(squash, COMPRESSION.get()); }

    public static boolean validCreatureScales(double kasumiMin,double kasumiMax,double emmaMin,double emmaMax) {
        return validPair(kasumiMin,kasumiMax) && validPair(emmaMin,emmaMax);
    }
    private static boolean validPair(double min,double max) {
        return Double.isFinite(min) && Double.isFinite(max) && min>=0.5 && max<=20 && min<=max;
    }
    private static void requireCreatureScales(double kasumiMin,double kasumiMax,double emmaMin,double emmaMax) {
        if (!validCreatureScales(kasumiMin,kasumiMax,emmaMin,emmaMax)) throw new IllegalArgumentException("Creature scales must be 0.5 to 20, with minimum <= maximum");
    }
    private static void checkCreatureScaleLimits() {
        assert validCreatureScales(0.75,1.25,0.75,1.25);
        assert validCreatureScales(0.5,20,20,20);
        assert !validCreatureScales(1.25,0.75,0.75,1.25);
        assert !validCreatureScales(0.75,1.25,2,1);
        assert !validCreatureScales(0.49,1.25,0.75,1.25);
        assert !validCreatureScales(0.75,20.01,0.75,1.25);
        assert !validCreatureScales(Double.NaN,1.25,0.75,1.25);
        assert !validCreatureScales(0.75,Double.POSITIVE_INFINITY,0.75,1.25);
    }

    /** Run with -ea. Checks native defaults, range correction and both scale-pair boundaries. */
    public static void main(String[] args) {
        checkCreatureScaleLimits();
        var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        SPEC.correct(config);
        assert SPEC.isCorrect(config);
        assert COMPRESSION.getDefault() == 50 && WIDTH.getDefault() == 50 && ROTATION_SPEED.getDefault().doubleValue() == 1.0;
        assert KASUMI_MIN_SCALE.getDefault().doubleValue()==0.75 && KASUMI_MAX_SCALE.getDefault().doubleValue()==1.25;
        assert EMMA_MIN_SCALE.getDefault().doubleValue()==0.75 && EMMA_MAX_SCALE.getDefault().doubleValue()==1.25;
        config.set("compressionPercent", 90); config.set("widthPercent", 200); config.set("rotationSpeed", 4.0);
        for (String key : new String[] {"kasumiMinScale","kasumiMaxScale","emmaMinScale","emmaMaxScale"}) {
            config.set(key,0.5); assert SPEC.isCorrect(config);
            config.set(key,20.0); assert SPEC.isCorrect(config);
            for (double invalid : new double[] {0.49,20.01,Double.NaN,Double.POSITIVE_INFINITY}) {
                config.set(key,invalid); assert !SPEC.isCorrect(config);
                SPEC.correct(config); assert SPEC.isCorrect(config);
            }
        }
        for (String key : new String[] {"compressionPercent", "widthPercent", "rotationSpeed"}) {
            Object valid = config.get(key);
            config.set(key, -1); assert !SPEC.isCorrect(config);
            SPEC.correct(config); assert SPEC.isCorrect(config);
            config.set(key, valid);
        }
        config.set("compressionPercent", 91); config.set("widthPercent", 201); config.set("rotationSpeed", Double.NaN);
        assert !SPEC.isCorrect(config); SPEC.correct(config); assert SPEC.isCorrect(config);
        System.out.println("Native config defaults, scale pairs, ranges and invalid value correction passed.");
    }
}
