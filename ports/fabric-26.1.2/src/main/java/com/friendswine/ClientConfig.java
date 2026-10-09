package com.friendswine;

/** Local display settings; server mechanics and clocks are independent of these values. */
public final class ClientConfig {
    public static final ConfigFile.Value<Integer> COMPRESSION=new ConfigFile.Value<>(50,0,90);
    public static final ConfigFile.Value<Integer> WIDTH=new ConfigFile.Value<>(50,0,200);
    public static final ConfigFile.Value<Double> ROTATION_SPEED=new ConfigFile.Value<>(1.0,0,4);
    public static final ConfigFile.Value<Double> KASUMI_MIN_SCALE=new ConfigFile.Value<>(0.75,0.5,20);
    public static final ConfigFile.Value<Double> KASUMI_MAX_SCALE=new ConfigFile.Value<>(1.25,0.5,20);
    public static final ConfigFile.Value<Double> EMMA_MIN_SCALE=new ConfigFile.Value<>(0.75,0.5,20);
    public static final ConfigFile.Value<Double> EMMA_MAX_SCALE=new ConfigFile.Value<>(1.25,0.5,20);

    public static void load() {
        var object=ConfigFile.read("client");
        COMPRESSION.read(object,"compressionPercent"); WIDTH.read(object,"widthPercent"); ROTATION_SPEED.read(object,"rotationSpeed");
        KASUMI_MIN_SCALE.read(object,"kasumiMinScale"); KASUMI_MAX_SCALE.read(object,"kasumiMaxScale");
        EMMA_MIN_SCALE.read(object,"emmaMinScale"); EMMA_MAX_SCALE.read(object,"emmaMaxScale");
        correctPair(KASUMI_MIN_SCALE,KASUMI_MAX_SCALE);
        correctPair(EMMA_MIN_SCALE,EMMA_MAX_SCALE);
    }
    private static void correctPair(ConfigFile.Value<Double> min,ConfigFile.Value<Double> max) {
        if (!validPair(min.get(),max.get())) { min.set(0.75); max.set(1.25); }
    }
    public static double creatureMin(boolean emma) {
        return emma ? EMMA_MIN_SCALE.get() : KASUMI_MIN_SCALE.get();
    }
    public static double creatureMax(boolean emma) {
        return emma ? EMMA_MAX_SCALE.get() : KASUMI_MAX_SCALE.get();
    }
    public static void save(int compression,int width,double rotation) {
        save(compression,width,rotation,creatureMin(false),creatureMax(false),creatureMin(true),creatureMax(true));
    }
    public static void save(int compression,int width,double rotation,double kasumiMin,double kasumiMax,double emmaMin,double emmaMax) {
        if (compression<0 || compression>90 || width<0 || width>200 || !Double.isFinite(rotation) || rotation<0 || rotation>4) throw new IllegalArgumentException("Invalid client settings");
        requireCreatureScales(kasumiMin,kasumiMax,emmaMin,emmaMax);
        var object=ConfigFile.read("client");
        object.addProperty("compressionPercent",compression); object.addProperty("widthPercent",width); object.addProperty("rotationSpeed",rotation);
        object.addProperty("kasumiMinScale",kasumiMin); object.addProperty("kasumiMaxScale",kasumiMax);
        object.addProperty("emmaMinScale",emmaMin); object.addProperty("emmaMaxScale",emmaMax);
        ConfigFile.write("client",object);
        COMPRESSION.set(compression); WIDTH.set(width); ROTATION_SPEED.set(rotation);
        KASUMI_MIN_SCALE.set(kasumiMin); KASUMI_MAX_SCALE.set(kasumiMax); EMMA_MIN_SCALE.set(emmaMin); EMMA_MAX_SCALE.set(emmaMax);
    }
    public static float heightScale(float squash) { return JellyAnimation.heightScale(squash,COMPRESSION.get()); }

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

    /** Run with -ea. Exercises real JSON numeric parsing and both independent scale pairs without touching files. */
    public static void main(String[] args) {
        checkCreatureScaleLimits();
        var object=new com.google.gson.JsonObject();
        object.addProperty("kasumiMinScale",20); object.addProperty("kasumiMaxScale",0.5);
        KASUMI_MIN_SCALE.read(object,"kasumiMinScale"); KASUMI_MAX_SCALE.read(object,"kasumiMaxScale");
        correctPair(KASUMI_MIN_SCALE,KASUMI_MAX_SCALE);
        assert creatureMin(false)==0.75 && creatureMax(false)==1.25;
        object.addProperty("kasumiMinScale",0.5); object.addProperty("kasumiMaxScale",20);
        object.addProperty("emmaMinScale",1); object.addProperty("emmaMaxScale",2);
        KASUMI_MIN_SCALE.read(object,"kasumiMinScale"); KASUMI_MAX_SCALE.read(object,"kasumiMaxScale");
        EMMA_MIN_SCALE.read(object,"emmaMinScale"); EMMA_MAX_SCALE.read(object,"emmaMaxScale");
        assert creatureMin(false)==0.5 && creatureMax(false)==20 && creatureMin(true)==1 && creatureMax(true)==2;
        object.addProperty("kasumiMinScale","invalid"); object.addProperty("emmaMaxScale",21);
        KASUMI_MIN_SCALE.read(object,"kasumiMinScale"); EMMA_MAX_SCALE.read(object,"emmaMaxScale");
        assert creatureMin(false)==0.75 && creatureMax(true)==1.25;
        System.out.println("Client config numeric parsing, scale limits, pair correction and independence passed.");
    }
}
