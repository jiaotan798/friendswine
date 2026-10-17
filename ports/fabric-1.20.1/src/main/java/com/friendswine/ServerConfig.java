package com.friendswine;

/** 物理环绕和死亡效果范围由服务端决定；保存时合并旧 JSON，保留其他配置。 */
public final class ServerConfig {
    public static final ConfigFile.Value<Double> ORBIT_SPEED = new ConfigFile.Value<>(1.0, 0, 4);
    public static DeathFlashScope DEATH_FLASH_SCOPE = DeathFlashScope.NEARBY;

    public static void load() {
        var object = ConfigFile.read("server");
        ORBIT_SPEED.read(object, "orbitSpeed");
        var entry = object.get("deathFlashScope");
        DEATH_FLASH_SCOPE = entry != null && entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()
                ? DeathFlashScope.fromConfig(entry.getAsString()) : DeathFlashScope.NEARBY;
    }
    public static DeathFlashScope deathFlashScope() { return DEATH_FLASH_SCOPE; }
    public static void save(double speed) { save(speed, DEATH_FLASH_SCOPE); }

    /** 先完成磁盘原子写入再提交内存状态，保存失败时不能让服务器悄悄采用未保存的选项。 */
    public static void save(double speed, DeathFlashScope scope) {
        if (!Double.isFinite(speed) || speed < 0 || speed > 4 || scope == null)
            throw new IllegalArgumentException("Invalid server settings");
        var object = ConfigFile.read("server");
        object.addProperty("orbitSpeed", speed);
        object.addProperty("deathFlashScope", scope.name());
        ConfigFile.write("server", object);
        ORBIT_SPEED.set(speed);
        DEATH_FLASH_SCOPE = scope;
    }
}
