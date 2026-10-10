package top.nalanquede.jukeboxmuse.config;

import java.nio.file.Path;

import com.google.gson.JsonObject;

/**
 * Per-player settings in config/jukeboxmuse/client.json. The music screen changes them and saves right away;
 * only touched on the client thread, except reads from the audio thread (volatile fields).
 */
public final class ClientSettings {
    private static ClientSettings current = new ClientSettings(null, 0.6, false, true);

    private final Path file;
    private volatile double volume;
    private volatile boolean muted;
    private volatile boolean lyricHud;

    private ClientSettings(Path file, double volume, boolean muted, boolean lyricHud) {
        this.file = file;
        this.volume = volume;
        this.muted = muted;
        this.lyricHud = lyricHud;
    }

    public static ClientSettings get() {
        return current;
    }

    /** Loads file (creating or normalising it) and makes the result current. */
    public static ClientSettings load(Path file) {
        JsonObject o = SettingsFile.read(file);
        ClientSettings defaults = current;
        current = new ClientSettings(file,
                SettingsFile.doubleIn(o, "volume", defaults.volume, 0, 1),
                SettingsFile.bool(o, "muted", defaults.muted),
                SettingsFile.bool(o, "lyricHud", defaults.lyricHud));
        current.save();
        return current;
    }

    /** Music volume 0–1, multiplied by the game's Master and Jukebox/Note Blocks volume. */
    public double volume() {
        return volume;
    }

    public boolean muted() {
        return muted;
    }

    /** Whether the current lyric line is shown at the top of the screen. */
    public boolean lyricHud() {
        return lyricHud;
    }

    public void setVolume(double volume) {
        this.volume = Math.max(0, Math.min(1, volume));
        save();
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        save();
    }

    public void setLyricHud(boolean lyricHud) {
        this.lyricHud = lyricHud;
        save();
    }

    private void save() {
        if (file == null) return;
        SettingsFile.write(file, "// Jukebox Muse 客户端设置，也可以在点歌界面（K）里直接调整。\n"
                + "{\n"
                + "  // 音乐音量（0 - 1），还会乘以游戏的「主音量」和「唱片机/音符盒」音量\n"
                + "  \"volume\": " + Math.round(volume * 100) / 100.0 + ",\n"
                + "  // 静音：不在这台电脑上播放点的歌\n"
                + "  \"muted\": " + muted + ",\n"
                + "  // 在屏幕顶部显示当前这句歌词\n"
                + "  \"lyricHud\": " + lyricHud + "\n"
                + "}\n");
    }
}
