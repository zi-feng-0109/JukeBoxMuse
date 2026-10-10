package top.nalanquede.jukeboxmuse.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.QueueEntry;
import top.nalanquede.jukeboxmuse.common.Song;
import top.nalanquede.jukeboxmuse.network.MusePayloads.AccountInfo;

/** What the client knows about the server's music state. Only touched on the client thread. */
public final class ClientState {
    private ClientState() {
    }

    // Search (searchPlatform is the platform the shown results came from)
    public static Platform searchPlatform = Platform.KUGOU;
    public static String searchKeyword = "";
    public static int searchPage = 1;
    public static List<Song> searchResults = List.of();
    public static String searchError = "";
    public static boolean searching;

    // Queue / now playing
    public static List<QueueEntry> queue = List.of();
    public static QueueEntry nowPlaying;
    public static Lyrics lyrics = Lyrics.EMPTY;
    /** Local wall-clock time the current song started, used when the audio thread has no position yet. */
    public static long startedAtMs;

    /** Where a search result stands, for its row label. PENDING lasts until the queue update or PENDING_MS. */
    public enum TrackState {NONE, PENDING, QUEUED, PLAYING}

    private static final long PENDING_MS = 4000;
    private static final Map<String, Long> pending = new HashMap<>();

    // Accounts (qrContent / loginState only sent to players who can manage them). Replaced, never mutated.
    public static boolean canManage;
    public static Map<Platform, AccountInfo> accounts = Map.of();

    public static long positionMs() {
        if (nowPlaying == null) return 0;
        long audio = AudioPlayer.positionMs();
        return audio >= 0 ? audio : System.currentTimeMillis() - startedAtMs;
    }

    public static void markPending(Song song) {
        pending.put(key(song), System.currentTimeMillis());
    }

    /** The server answered with a new queue: requests are now either in it or were refused. */
    public static void clearPending() {
        pending.clear();
    }

    public static TrackState trackState(Song song) {
        if (nowPlaying != null && nowPlaying.song().sameTrack(song)) return TrackState.PLAYING;
        for (QueueEntry e : queue) {
            if (e.song().sameTrack(song)) return TrackState.QUEUED;
        }
        Long since = pending.get(key(song));
        return since != null && System.currentTimeMillis() - since < PENDING_MS ? TrackState.PENDING : TrackState.NONE;
    }

    private static String key(Song song) {
        return song.platform().id() + ":" + song.id().toLowerCase();
    }

    public static AccountInfo account(Platform platform) {
        AccountInfo a = accounts.get(platform);
        return a != null ? a : new AccountInfo(platform, false, "", false, "", "");
    }

    public static boolean loggedIn(Platform platform) {
        return account(platform).loggedIn();
    }

    public static boolean anyLoggedIn() {
        return accounts.values().stream().anyMatch(AccountInfo::loggedIn);
    }

    public static void reset() {
        searchPlatform = Platform.KUGOU;
        searchKeyword = "";
        searchPage = 1;
        searchResults = List.of();
        searchError = "";
        searching = false;
        queue = List.of();
        nowPlaying = null;
        lyrics = Lyrics.EMPTY;
        canManage = false;
        accounts = Map.of();
        pending.clear();
    }
}
