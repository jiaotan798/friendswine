package com.friendswine;

public final class ServerConfig {
    public static final ConfigFile.Value<Double> ORBIT_SPEED=new ConfigFile.Value<>(1.0,0,4);
    public static void load() { ORBIT_SPEED.read(ConfigFile.read("server"),"orbitSpeed"); }
    public static void save(double speed) {
        if (!Double.isFinite(speed) || speed<0 || speed>4) throw new IllegalArgumentException("Invalid orbit speed");
        var object=ConfigFile.read("server"); object.addProperty("orbitSpeed",speed); ConfigFile.write("server",object); ORBIT_SPEED.set(speed);
    }
}
