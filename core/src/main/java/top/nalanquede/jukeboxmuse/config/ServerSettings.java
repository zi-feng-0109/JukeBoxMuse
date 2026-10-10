package top.nalanquede.jukeboxmuse.config;

import java.nio.file.Path;

import com.google.gson.JsonObject;

/**
 * Server-side settings in config/jukeboxmuse/server.json, read when the server (or a single-player world) starts.
 * Immutable: edit the file and restart the server / reopen the world to change them.
 */
public record ServerSettings(int cooldownSeconds, int maxQueuedPerPlayer, int maxQueueSize, String quality, boolean debugLog) {
    // debugLog is off by default: raw responses can contain login tokens, and players paste whole logs when asking for help
    public static final ServerSettings DEFAULTS = new ServerSettings(30, 3, 50, "128", false);

    private static volatile ServerSettings current = DEFAULTS;

    /** The settings of the running server; DEFAULTS before one has started. */
    public static ServerSettings current() {
        return current;
    }

    /** Loads file (creating or normalising it) and makes the result current. */
    public static ServerSettings load(Path file) {
        JsonObject o = SettingsFile.read(file);
        String quality = SettingsFile.string(o, "quality", DEFAULTS.quality);
        ServerSettings s = new ServerSettings(
                SettingsFile.intIn(o, "cooldownSeconds", DEFAULTS.cooldownSeconds, 0, 3600),
                SettingsFile.intIn(o, "maxQueuedPerPlayer", DEFAULTS.maxQueuedPerPlayer, 1, 100),
                SettingsFile.intIn(o, "maxQueueSize", DEFAULTS.maxQueueSize, 1, 1000),
                "320".equals(quality) ? "320" : "128",
                SettingsFile.bool(o, "debugLog", DEFAULTS.debugLog));
        SettingsFile.write(file, s.toText());
        current = s;
        return s;
    }

    String toText() {
        return "// Jukebox Muse 服务端设置。修改后重启服务器（单人游戏重新进入世界）生效。\n"
                + "{\n"
                + "  // 每个玩家两次点歌之间至少间隔多少秒（0 - 3600），管理员不受限制\n"
                + "  \"cooldownSeconds\": " + cooldownSeconds + ",\n"
                + "  // 每个玩家最多同时有几首歌在排队（1 - 100），管理员不受限制\n"
                + "  \"maxQueuedPerPlayer\": " + maxQueuedPerPlayer + ",\n"
                + "  // 整个点歌队列最多多少首（1 - 1000）\n"
                + "  \"maxQueueSize\": " + maxQueueSize + ",\n"
                + "  // MP3 音质：\"128\" 或 \"320\"。320 在多数平台需要 VIP 账号，拿不到时退回 128\n"
                + "  \"quality\": " + SettingsFile.quote(quality) + ",\n"
                + "  // 在日志里记录各音乐平台的原始返回，排查问题时再打开（会包含账号登录凭证，分享日志前务必关掉并删除旧日志）\n"
                + "  \"debugLog\": " + debugLog + "\n"
                + "}\n";
    }
}
