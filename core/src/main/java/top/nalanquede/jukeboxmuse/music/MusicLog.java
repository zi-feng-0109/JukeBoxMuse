package top.nalanquede.jukeboxmuse.music;

import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logging for the platform clients. The mod decides whether raw responses are logged (its debugLog option). */
public final class MusicLog {
    public static final Logger LOGGER = LoggerFactory.getLogger("JukeboxMuse");

    private static volatile BooleanSupplier debug = () -> true;

    private MusicLog() {
    }

    public static void setDebug(BooleanSupplier enabled) {
        debug = enabled;
    }

    /** Whether raw platform responses should be logged. */
    public static boolean debug() {
        return debug.getAsBoolean();
    }

    /** Logs a raw response, cut to 4000 characters. */
    public static void response(String tag, String text) {
        if (debug()) LOGGER.info("[{}] -> {}", tag, text.length() > 4000 ? text.substring(0, 4000) + "..." : text);
    }
}
