package top.nalanquede.jukeboxmuse.client;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.QueueEntry;
import top.nalanquede.jukeboxmuse.common.Song;
import top.nalanquede.jukeboxmuse.lyrics.Lyrics;
import top.nalanquede.jukeboxmuse.network.MusePayloads.AccountInfo;

/**
 * Development aid for checking the rendering on every version and loader (scripts/smoke-test.sh --screenshots):
 * with -Djukeboxmuse.screenshotTour=<name>, once in a world the client fills in sample state, opens the music screen,
 * screenshots each tab into screenshots/jukeboxmuse-<name>-*.png and quits. Does nothing without the property.
 * Covers stay the vanilla discs: there is no cover server here.
 */
final class ScreenshotTour {
    private static final String NAME = System.getProperty("jukeboxmuse.screenshotTour", "");
    private static final String[] TABS = {"NOW", "SEARCH", "QUEUE", "ACCOUNT"};

    private static int ticks;
    private static int step;

    private ScreenshotTour() {
    }

    /** After every client tick. */
    static void tick(Minecraft mc) {
        if (NAME.isEmpty() || mc.player == null) return;
        screenshot(mc);
        // Let the world render and the server's join-time state arrive first, then wait a while between steps
        if (++ticks < (step == 0 ? 100 : 15)) return;
        ticks = 0;
        if (step == 0) {
            fill();
        } else if (step <= TABS.length) {
            setTab(TABS[step - 1]);
            McGui.setScreen(mc, new MusicScreen());
        } else if (step == TABS.length + 1) {
            McGui.setScreen(mc, null);
        } else {
            JukeboxMuse.LOGGER.info("Screenshot tour finished");
            mc.stop();
            return;
        }
        step++;
    }

    /**
     * Screenshots the current step once, 10 ticks into it. Called at the start of a client tick, between frames, so
     * the main render target holds the whole last frame (from 1.21.6 the GUI is drawn at the end of the frame).
     */
    private static int shotAtStep = -1;

    private static void screenshot(Minecraft mc) {
        if (ticks < 10 || step < 2 || shotAtStep == step) return;
        shotAtStep = step;
        String shot = step - 2 < TABS.length ? "tab-" + TABS[step - 2].toLowerCase() : "hud";
        Screenshot.grab(new File(mc.gameDirectory.getPath()), "jukeboxmuse-" + NAME + "-" + shot + ".png", /*? if >=26.2 {*//*mc.gameRenderer.mainRenderTarget()*//*?} else {*/mc.getMainRenderTarget()/*?}*/,
                /*? if >=1.21.6 {*//*1, *//*?}*/
                message -> JukeboxMuse.LOGGER.info("Screenshot tour: {}", message.getString()));
    }

    private static void fill() {
        Map<Platform, AccountInfo> accounts = new EnumMap<>(Platform.class);
        accounts.put(Platform.KUGOU, new AccountInfo(Platform.KUGOU, true, "测试账号", false, "", ""));
        accounts.put(Platform.NETEASE, new AccountInfo(Platform.NETEASE, true, "Test VIP", true, "", ""));
        accounts.put(Platform.QQ, new AccountInfo(Platform.QQ, false, "", false, "", ""));
        ClientState.accounts = accounts;
        ClientState.canManage = true;

        List<Song> results = new ArrayList<>();
        for (int i = 1; i <= 6; i++) results.add(song("r" + i, "搜索结果 " + i));
        ClientState.searchResults = results;
        ClientState.searchKeyword = "测试";

        List<QueueEntry> queue = new ArrayList<>();
        queue.add(new QueueEntry(results.get(1), "Player0"));
        for (int i = 2; i <= 10; i++) queue.add(new QueueEntry(song("q" + i, "排队歌曲 " + i), "Player" + i));
        ClientState.queue = queue;

        ClientState.nowPlaying = new QueueEntry(results.get(3), "Player0");
        ClientState.lyrics = Lyrics.parse("[00:00.00]第一句歌词\n[00:01.00]第二句歌词，现在唱到这里\n[00:30.00]第三句歌词\n",
                "[00:00.00]First line\n[00:01.00]Second line, sung now\n[00:30.00]Third line\n");
        ClientState.startedAtMs = System.currentTimeMillis() - 5_000;
    }

    private static Song song(String id, String name) {
        return new Song(Platform.KUGOU, id, "", 0, 0, name, "测试歌手", "测试专辑", 215_000, false, "");
    }

    /** MusicScreen remembers its tab in a private static field. */
    private static void setTab(String name) {
        try {
            Field f = MusicScreen.class.getDeclaredField("tab");
            f.setAccessible(true);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class) f.getType(), name);
            f.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
