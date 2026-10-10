package top.nalanquede.kgmusic;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class KgConfig {
    private KgConfig() {
    }

    // ---------- common (read on the server) ----------
    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS = COMMON
            .comment("Seconds a player must wait between two song requests")
            .defineInRange("cooldownSeconds", 30, 0, 3600);

    public static final ModConfigSpec.IntValue MAX_PER_PLAYER = COMMON
            .comment("How many songs one player may have waiting in the queue")
            .defineInRange("maxQueuedPerPlayer", 3, 1, 100);

    public static final ModConfigSpec.IntValue MAX_QUEUE = COMMON
            .comment("Maximum total queue length")
            .defineInRange("maxQueueSize", 50, 1, 1000);

    public static final ModConfigSpec.ConfigValue<String> QUALITY = COMMON
            .comment("MP3 quality for every platform: 128 or 320 (320 needs a VIP account on most platforms and falls back to 128)")
            .define("quality", "128", v -> "128".equals(v) || "320".equals(v));

    public static final ModConfigSpec.BooleanValue DEBUG_LOG = COMMON
            .comment("Log raw KuGou / NetEase / QQ Music responses (useful when a platform changes its API)")
            .define("debugLog", true);

    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    // ---------- client ----------
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue VOLUME = CLIENT
            .comment("Music volume, multiplied by the game's Master and Jukebox/Note Blocks volume")
            .defineInRange("volume", 0.6, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue MUTED = CLIENT
            .comment("Do not play requested songs on this client")
            .define("muted", false);

    public static final ModConfigSpec.BooleanValue LYRIC_HUD = CLIENT
            .comment("Show the current lyric line at the top of the screen")
            .define("lyricHud", true);

    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();
}
