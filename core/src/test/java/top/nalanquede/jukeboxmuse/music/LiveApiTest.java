package top.nalanquede.jukeboxmuse.music;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import top.nalanquede.jukeboxmuse.common.LoginMethod;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.Song;
import top.nalanquede.jukeboxmuse.kugou.KugouProvider;
import top.nalanquede.jukeboxmuse.netease.NeteaseApi;
import top.nalanquede.jukeboxmuse.qqmusic.QqMusicApi;

/** Hits the real NetEase / QQ Music servers anonymously. Run with JUKEBOXMUSE_LIVE=1. */
@EnabledIfEnvironmentVariable(named = "JUKEBOXMUSE_LIVE", matches = "1")
class LiveApiTest {
    @TempDir
    Path dir;

    @Test
    void netease() throws Exception {
        MusicProvider api = new NeteaseApi(dir.resolve("netease.json"));
        List<Song> songs = api.search("Taylor Swift", 1, 5);
        assertFalse(songs.isEmpty());
        Song s = songs.get(0);
        System.out.println("netease: " + songs);
        assertTrue(s.durationMs() > 0);
        MusicProvider.LyricText lyric = api.lyric(s);
        System.out.println("netease lyric: " + lyric.lrc().lines().skip(5).limit(2).toList() + " / " + lyric.translation().lines().skip(5).limit(2).toList());
        assertTrue(lyric.translation().contains("[0"));
        try {
            System.out.println("netease url: " + api.songUrl(s, "128"));
        } catch (MusicException e) {
            // Expected anonymously: only previews of VIP songs
            System.out.println("netease url refused: " + e.getMessage());
        }
        MusicProvider.LoginQr qr = api.loginQr(LoginMethod.NETEASE);
        System.out.println("netease qr: " + qr.content());
        assertTrue(qr.content().startsWith("https://music.163.com/login?codekey="));
        assertTrue(api.loginCheck(qr) == MusicProvider.LoginStatus.WAITING);
    }

    @Test
    void qq() throws Exception {
        MusicProvider api = new QqMusicApi(dir.resolve("qqmusic.json"));
        List<Song> songs = api.search("晴天", 1, 10);
        assertFalse(songs.isEmpty());
        System.out.println("qq: " + songs);
        Song s = songs.get(0);
        assertTrue(s.durationMs() > 0 && !s.fileId().isEmpty());
        assertTrue(api.lyric(s).lrc().contains("[00:"));
        MusicProvider.LyricText english = api.lyric(api.search("Love Story Taylor Swift", 1, 1).get(0));
        System.out.println("qq translation: " + english.translation().lines().skip(6).limit(2).toList());
        assertTrue(english.translation().contains("[0"));
        Song free = songs.stream().filter(x -> !x.vip()).findFirst().orElseThrow();
        String url = api.songUrl(free, "128");
        System.out.println("qq url: " + url);
        assertTrue(url.startsWith("https://"));

        MusicProvider.LoginQr qq = api.loginQr(LoginMethod.QQ);
        System.out.println("qq qr: " + qq.content());
        assertTrue(qq.content().contains("txz.qq.com"));
        assertTrue(api.loginCheck(qq) == MusicProvider.LoginStatus.WAITING);
        MusicProvider.LoginQr wx = api.loginQr(LoginMethod.WECHAT);
        System.out.println("wechat qr: " + wx.content());
        assertTrue(wx.content().startsWith("https://open.weixin.qq.com/connect/confirm?uuid="));
    }

    @Test
    void kugouLyricTranslation() throws Exception {
        MusicProvider api = new KugouProvider(dir.resolve("account.json"));
        api.prepare();
        // Searching needs a logged-in account; the lyric lookup works from title + duration alone
        Song s = new Song(Platform.KUGOU, "", "", 0, 0, "Love Story", "Taylor Swift", "", 236000, false, "");
        MusicProvider.LyricText lyric = api.lyric(s);
        System.out.println("kugou lyric: " + lyric.lrc().lines().skip(4).limit(2).toList() + " / translation: " + lyric.translation().lines().limit(2).toList());
        // This hash-less lookup lands on a user-uploaded lyric without translation, so only the conversion is checked
        assertTrue(lyric.lrc().contains("See you make your way"));
    }
}
