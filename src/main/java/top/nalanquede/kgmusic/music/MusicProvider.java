package top.nalanquede.kgmusic.music;

import java.util.List;

import top.nalanquede.kgmusic.common.LoginMethod;
import top.nalanquede.kgmusic.common.Platform;
import top.nalanquede.kgmusic.common.Song;

/**
 * One music platform with its single server-wide account. Every call except the account getters blocks on the
 * network; MusicService runs them off the server thread.
 */
public interface MusicProvider {
    /** A QR login in progress: key identifies it to the platform, content is the text the QR code encodes. */
    record LoginQr(LoginMethod method, String key, String content) {
    }

    enum LoginStatus {WAITING, SCANNED, SUCCESS, EXPIRED, REFUSED}

    /** Original LRC plus an optional translation LRC with matching timestamps; "" when missing. */
    record LyricText(String lrc, String translation) {
        public static final LyricText EMPTY = new LyricText("", "");
    }

    Platform platform();

    boolean isLoggedIn();

    String nickname();

    boolean vip();

    /** Runs before every call; KuGou registers its device here. */
    default void prepare() {
    }

    List<Song> search(String keyword, int page, int pageSize) throws MusicException;

    /** A playable MP3 URL. quality is "128" or "320"; providers fall back to what the account can get. */
    String songUrl(Song song, String quality) throws MusicException;

    LyricText lyric(Song song) throws MusicException;

    LoginQr loginQr(LoginMethod method) throws MusicException;

    /** On SUCCESS the provider has already stored the new account. */
    LoginStatus loginCheck(LoginQr qr) throws MusicException;

    /** When the session was last refreshed; MusicService calls refresh() once a day. */
    long lastRefreshMs();

    void refresh() throws MusicException;

    void logout();
}
