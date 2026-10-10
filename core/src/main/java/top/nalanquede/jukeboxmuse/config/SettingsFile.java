package top.nalanquede.jukeboxmuse.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import top.nalanquede.jukeboxmuse.music.MusicLog;

/**
 * Reading and writing the settings files. They are JSON with // comments so server owners can see what each value
 * does; Gson's lenient mode reads the comments back. Files are rewritten after loading, which fills in new keys and
 * replaces invalid values with the clamped ones actually in use.
 */
final class SettingsFile {
    private static final Gson GSON = new Gson();

    private SettingsFile() {
    }

    /** The file's object, or an empty one when it is missing or unreadable (defaults then apply). */
    static JsonObject read(Path file) {
        if (!Files.exists(file)) return new JsonObject();
        try {
            JsonReader reader = new JsonReader(new StringReader(Files.readString(file, StandardCharsets.UTF_8)));
            reader.setLenient(true);
            JsonElement element = JsonParser.parseReader(reader);
            if (element.isJsonObject()) return element.getAsJsonObject();
        } catch (Exception e) {
            MusicLog.LOGGER.warn("Cannot read {}, using defaults: {}", file, e.toString());
        }
        return new JsonObject();
    }

    static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            MusicLog.LOGGER.warn("Cannot save {}: {}", file, e.toString());
        }
    }

    static int intIn(JsonObject o, String key, int def, int min, int max) {
        try {
            return o.has(key) ? Math.max(min, Math.min(max, o.get(key).getAsInt())) : def;
        } catch (Exception e) {
            return def;
        }
    }

    static double doubleIn(JsonObject o, String key, double def, double min, double max) {
        try {
            return o.has(key) ? Math.max(min, Math.min(max, o.get(key).getAsDouble())) : def;
        } catch (Exception e) {
            return def;
        }
    }

    static boolean bool(JsonObject o, String key, boolean def) {
        try {
            return o.has(key) ? o.get(key).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    static String string(JsonObject o, String key, String def) {
        try {
            return o.has(key) ? o.get(key).getAsString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    /** A JSON string literal, quoted and escaped. */
    static String quote(String s) {
        return GSON.toJson(s);
    }
}
