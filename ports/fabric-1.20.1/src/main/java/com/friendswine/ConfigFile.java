package com.friendswine;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static Path path(String side) { return FabricLoader.getInstance().getConfigDir().resolve("friendswine-" + side + ".json"); }
    static JsonObject read(String side) {
        Path path = path(side);
        if (!Files.exists(path)) return new JsonObject();
        try (var reader = Files.newBufferedReader(path)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        catch (IOException | RuntimeException error) {
            System.err.println("[friendswine] Cannot read " + path + "; using defaults until settings are saved: " + error.getMessage());
            return new JsonObject();
        }
    }
    static void write(String side, JsonObject object) {
        Path target = path(side), temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temporary, GSON.toJson(object));
            try { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) { throw new IllegalStateException("Cannot save friendswine settings",error); }
    }
    public static final class Value<T extends Number> {
        private final T initial;
        private final double min, max;
        private T value;
        Value(T initial,double min,double max) { this.initial=initial; this.value=initial; this.min=min; this.max=max; }
        public T get() { return value; }
        public T getDefault() { return initial; }
        public void set(T value) {
            double number=value.doubleValue();
            if (!Double.isFinite(number) || number<min || number>max) throw new IllegalArgumentException("Setting outside range");
            this.value=value;
        }
        @SuppressWarnings("unchecked") void read(JsonObject object,String key) {
            value=initial;
            try {
                var entry=object.get(key);
                if (entry == null || !entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isNumber()) return;
                double number=entry.getAsDouble();
                if (!Double.isFinite(number) || number<min || number>max || initial instanceof Integer && number!=Math.rint(number)) return;
                if (initial instanceof Integer) value=(T) Integer.valueOf((int) number);
                else value=(T) Double.valueOf(number);
            } catch (RuntimeException invalid) { value=initial; }
        }
    }
}
