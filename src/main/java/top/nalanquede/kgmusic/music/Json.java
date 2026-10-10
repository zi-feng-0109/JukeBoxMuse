package top.nalanquede.kgmusic.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Null-tolerant accessors: the platforms' responses are undocumented and fields come and go. */
public final class Json {
    private Json() {
    }

    public static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    public static String str(JsonObject o, String key, String def) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    public static long num(JsonObject o, String key, long def) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsLong();
        } catch (NumberFormatException ex) {
            try {
                return (long) Double.parseDouble(e.getAsString());
            } catch (NumberFormatException ex2) {
                return def;
            }
        }
    }
}
