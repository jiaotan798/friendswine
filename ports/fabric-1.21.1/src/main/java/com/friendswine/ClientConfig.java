package com.friendswine;

/** 三种角色各自的本地显示设置；不改变服务端行为与音乐时钟。 */
public final class ClientConfig {
    public static final ConfigFile.Value<Integer> COMPRESSION=new ConfigFile.Value<>(50,0,90);
    public static final ConfigFile.Value<Integer> WIDTH=new ConfigFile.Value<>(50,0,200);
    public static final ConfigFile.Value<Double> ROTATION_SPEED=new ConfigFile.Value<>(1.0,0,4);
    public static final ConfigFile.Value<Double> KASUMI_MIN_SCALE=new ConfigFile.Value<>(0.75,0.5,20);
    public static final ConfigFile.Value<Double> KASUMI_MAX_SCALE=new ConfigFile.Value<>(1.25,0.5,20);
    public static final ConfigFile.Value<Double> EMMA_MIN_SCALE=new ConfigFile.Value<>(0.75,0.5,20);
    public static final ConfigFile.Value<Double> AMIYA_MIN_SCALE=new ConfigFile.Value<>(0.75,0.5,20);
    public static final ConfigFile.Value<Double> EMMA_MAX_SCALE=new ConfigFile.Value<>(1.25,0.5,20);
    public static final ConfigFile.Value<Double> AMIYA_MAX_SCALE=new ConfigFile.Value<>(1.25,0.5,20);

    public static void load() {
        var object=ConfigFile.read("client");
        COMPRESSION.read(object,"compressionPercent"); WIDTH.read(object,"widthPercent"); ROTATION_SPEED.read(object,"rotationSpeed");
        KASUMI_MIN_SCALE.read(object,"kasumiMinScale"); KASUMI_MAX_SCALE.read(object,"kasumiMaxScale");
        EMMA_MIN_SCALE.read(object,"emmaMinScale"); EMMA_MAX_SCALE.read(object,"emmaMaxScale");
        AMIYA_MIN_SCALE.read(object,"amiyaMinScale"); AMIYA_MAX_SCALE.read(object,"amiyaMaxScale");
        correctPair(KASUMI_MIN_SCALE,KASUMI_MAX_SCALE);
        correctPair(EMMA_MIN_SCALE,EMMA_MAX_SCALE);
        correctPair(AMIYA_MIN_SCALE,AMIYA_MAX_SCALE);
    }
    private static void correctPair(ConfigFile.Value<Double> min,ConfigFile.Value<Double> max) {
        if (!validPair(min.get(),max.get())) { min.set(0.75); max.set(1.25); }
    }
    public static double creatureMin(GuestVariant variant) {
        return switch (variant) { case KASUMI -> KASUMI_MIN_SCALE.get(); case EMMA -> EMMA_MIN_SCALE.get(); case AMIYA -> AMIYA_MIN_SCALE.get(); };
    }
    public static double creatureMax(GuestVariant variant) {
        return switch (variant) { case KASUMI -> KASUMI_MAX_SCALE.get(); case EMMA -> EMMA_MAX_SCALE.get(); case AMIYA -> AMIYA_MAX_SCALE.get(); };
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
        // 旧调用只更新原有选项，不能把新增角色的已有配置重置为默认值。
        save(compression,width,rotation,kasumiMin,kasumiMax,emmaMin,emmaMax,creatureMin(GuestVariant.AMIYA),creatureMax(GuestVariant.AMIYA));
    }
    /** 校验三组独立大小后一起保存；失败时不会部分写入配置。 */
    public static void save(int compression,int width,double rotation,double kasumiMin,double kasumiMax,double emmaMin,double emmaMax,double amiyaMin,double amiyaMax) {
        if (compression<0 || compression>90 || width<0 || width>200 || !Double.isFinite(rotation) || rotation<0 || rotation>4) throw new IllegalArgumentException("Invalid client settings");
        requireCreatureScales(kasumiMin,kasumiMax,emmaMin,emmaMax);
        if (!validPair(amiyaMin,amiyaMax)) throw new IllegalArgumentException("Invalid Amiya scales");
        var object=ConfigFile.read("client");
        object.addProperty("compressionPercent",compression); object.addProperty("widthPercent",width); object.addProperty("rotationSpeed",rotation);
        object.addProperty("kasumiMinScale",kasumiMin); object.addProperty("kasumiMaxScale",kasumiMax);
        object.addProperty("emmaMinScale",emmaMin); object.addProperty("emmaMaxScale",emmaMax);
        object.addProperty("amiyaMinScale",amiyaMin); object.addProperty("amiyaMaxScale",amiyaMax);
        ConfigFile.write("client",object);
        COMPRESSION.set(compression); WIDTH.set(width); ROTATION_SPEED.set(rotation);
        KASUMI_MIN_SCALE.set(kasumiMin); KASUMI_MAX_SCALE.set(kasumiMax); EMMA_MIN_SCALE.set(emmaMin); EMMA_MAX_SCALE.set(emmaMax); AMIYA_MIN_SCALE.set(amiyaMin); AMIYA_MAX_SCALE.set(amiyaMax);
    }
    public static float heightScale(float squash) { return JellyAnimation.heightScale(squash,COMPRESSION.get()); }

    public static boolean validCreatureScales(double kasumiMin,double kasumiMax,double emmaMin,double emmaMax) {
        return validPair(kasumiMin,kasumiMax) && validPair(emmaMin,emmaMax);
    }
    public static boolean validCreatureScales(double kasumiMin,double kasumiMax,double emmaMin,double emmaMax,double amiyaMin,double amiyaMax) {
        return validCreatureScales(kasumiMin,kasumiMax,emmaMin,emmaMax) && validPair(amiyaMin,amiyaMax);
    }
    private static boolean validPair(double min,double max) {
        return Double.isFinite(min) && Double.isFinite(max) && min>=0.5 && max<=20 && min<=max;
    }
    private static void requireCreatureScales(double kasumiMin,double kasumiMax,double emmaMin,double emmaMax) {
        if (!validCreatureScales(kasumiMin,kasumiMax,emmaMin,emmaMax)) throw new IllegalArgumentException("Creature scales must be 0.5 to 20, with minimum <= maximum");
    }
    private static void checkCreatureScaleLimits() {
        assert validCreatureScales(0.75,1.25,0.75,1.25,0.5,20);
        assert !validCreatureScales(0.75,1.25,0.75,1.25,2,1);
        assert !validCreatureScales(0.75,1.25,0.75,1.25,Double.NaN,20);
        assert validCreatureScales(0.75,1.25,0.75,1.25);
        assert validCreatureScales(0.5,20,20,20);
        assert !validCreatureScales(1.25,0.75,0.75,1.25);
        assert !validCreatureScales(0.75,1.25,2,1);
        assert !validCreatureScales(0.49,1.25,0.75,1.25);
        assert !validCreatureScales(0.75,20.01,0.75,1.25);
        assert !validCreatureScales(Double.NaN,1.25,0.75,1.25);
        assert !validCreatureScales(0.75,Double.POSITIVE_INFINITY,0.75,1.25);
    }

    /** 用 -ea 验证三组边界、旧 JSON 加载及独立设置，不读写磁盘。 */
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
        AMIYA_MIN_SCALE.read(object,"amiyaMinScale"); AMIYA_MAX_SCALE.read(object,"amiyaMaxScale");
        assert creatureMin(false)==0.5 && creatureMax(false)==20 && creatureMin(true)==1 && creatureMax(true)==2;
        // 旧 JSON 没有阿米娅字段：保留原有两组值，仅新增一组默认值。
        assert creatureMin(GuestVariant.AMIYA)==0.75 && creatureMax(GuestVariant.AMIYA)==1.25;
        object.addProperty("amiyaMinScale",2); object.addProperty("amiyaMaxScale",3);
        AMIYA_MIN_SCALE.read(object,"amiyaMinScale"); AMIYA_MAX_SCALE.read(object,"amiyaMaxScale");
        correctPair(AMIYA_MIN_SCALE,AMIYA_MAX_SCALE);
        assert creatureMin(GuestVariant.AMIYA)==2 && creatureMax(GuestVariant.AMIYA)==3;
        assert creatureMin(false)==0.5 && creatureMax(true)==2;
        object.addProperty("amiyaMaxScale",1);
        AMIYA_MAX_SCALE.read(object,"amiyaMaxScale"); correctPair(AMIYA_MIN_SCALE,AMIYA_MAX_SCALE);
        assert creatureMin(GuestVariant.AMIYA)==0.75 && creatureMax(GuestVariant.AMIYA)==1.25;
        object.addProperty("kasumiMinScale","invalid"); object.addProperty("emmaMaxScale",21);
        KASUMI_MIN_SCALE.read(object,"kasumiMinScale"); EMMA_MAX_SCALE.read(object,"emmaMaxScale");
        assert creatureMin(false)==0.75 && creatureMax(true)==1.25;
        System.out.println("Client config numeric parsing, scale limits, pair correction and independence passed.");
    }
}
