package top.nalanquede.jukeboxmuse.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Loads and saves a platform's account object as pretty-printed JSON under config/jukeboxmuse/. */
public final class AccountFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AccountFile() {
    }

    public static <T> T load(Path file, Class<T> type, Supplier<T> fresh) {
        if (Files.exists(file)) {
            try {
                T value = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
                if (value != null) return value;
            } catch (Exception ignored) {
                // Corrupt file: start over with a fresh account
            }
        }
        return fresh.get();
    }

    public static void save(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save " + file, e);
        }
    }
}
