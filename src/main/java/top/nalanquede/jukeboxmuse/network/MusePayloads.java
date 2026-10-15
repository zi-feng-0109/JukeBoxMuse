package top.nalanquede.jukeboxmuse.network;

import java.util.List;

import top.nalanquede.jukeboxmuse.common.LoginMethod;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.QueueEntry;
import top.nalanquede.jukeboxmuse.common.Song;

/**
 * Every packet the mod sends. C2S = client to server, S2C = server to client. These know nothing about Minecraft's
 * packet classes, which differ between versions; {@link McPacket} and the loaders carry them.
 */
public final class MusePayloads {
    private MusePayloads() {
    }

    /** A packet of the mod. */
    public interface Payload {
        PayloadType<?> type();
    }

    // ---------- client -> server ----------

    /** Sent when the music screen opens, so the server replies with queue + account state. */
    public record HelloC2S() implements Payload {
        public static final HelloC2S INSTANCE = new HelloC2S();
        public static final PayloadType<HelloC2S> TYPE = new PayloadType<>("hello", MuseCodec.unit(INSTANCE));

        @Override
        public PayloadType<HelloC2S> type() {
            return TYPE;
        }
    }

    public record SearchC2S(Platform platform, String keyword, int page) implements Payload {
        public static final PayloadType<SearchC2S> TYPE = new PayloadType<>("search", MuseCodec.of((buf, p) -> {
            MuseCodecs.PLATFORM.encode(buf, p.platform());
            MuseCodecs.KEYWORD.encode(buf, p.keyword());
            MuseCodec.VAR_INT.encode(buf, p.page());
        }, buf -> new SearchC2S(MuseCodecs.PLATFORM.decode(buf), MuseCodecs.KEYWORD.decode(buf), MuseCodec.VAR_INT.decode(buf))));

        @Override
        public PayloadType<SearchC2S> type() {
            return TYPE;
        }
    }

    public record RequestSongC2S(Song song) implements Payload {
        public static final PayloadType<RequestSongC2S> TYPE = new PayloadType<>("request_song", MuseCodecs.SONG.map(RequestSongC2S::new, RequestSongC2S::song));

        @Override
        public PayloadType<RequestSongC2S> type() {
            return TYPE;
        }
    }

    public record SkipC2S() implements Payload {
        public static final SkipC2S INSTANCE = new SkipC2S();
        public static final PayloadType<SkipC2S> TYPE = new PayloadType<>("skip", MuseCodec.unit(INSTANCE));

        @Override
        public PayloadType<SkipC2S> type() {
            return TYPE;
        }
    }

    /** Remove the queue entry at index (0 = next song after the current one). */
    public record RemoveC2S(int index) implements Payload {
        public static final PayloadType<RemoveC2S> TYPE = new PayloadType<>("remove", MuseCodec.VAR_INT.map(RemoveC2S::new, RemoveC2S::index));

        @Override
        public PayloadType<RemoveC2S> type() {
            return TYPE;
        }
    }

    /** Start (or re-show) a QR login from the account page. */
    public record LoginStartC2S(LoginMethod method) implements Payload {
        public static final PayloadType<LoginStartC2S> TYPE = new PayloadType<>("login_start", MuseCodecs.LOGIN_METHOD.map(LoginStartC2S::new, LoginStartC2S::method));

        @Override
        public PayloadType<LoginStartC2S> type() {
            return TYPE;
        }
    }

    public record LogoutC2S(Platform platform) implements Payload {
        public static final PayloadType<LogoutC2S> TYPE = new PayloadType<>("logout", MuseCodecs.PLATFORM.map(LogoutC2S::new, LogoutC2S::platform));

        @Override
        public PayloadType<LogoutC2S> type() {
            return TYPE;
        }
    }

    // ---------- server -> client ----------

    /** error is empty on success. */
    public record SearchResultS2C(Platform platform, String keyword, int page, List<Song> songs, String error) implements Payload {
        private static final MuseCodec<List<Song>> SONGS = MuseCodecs.SONG.list();
        public static final PayloadType<SearchResultS2C> TYPE = new PayloadType<>("search_result", MuseCodec.of((buf, p) -> {
            MuseCodecs.PLATFORM.encode(buf, p.platform());
            MuseCodec.STRING.encode(buf, p.keyword());
            MuseCodec.VAR_INT.encode(buf, p.page());
            SONGS.encode(buf, p.songs());
            MuseCodec.STRING.encode(buf, p.error());
        }, buf -> new SearchResultS2C(MuseCodecs.PLATFORM.decode(buf), MuseCodec.STRING.decode(buf), MuseCodec.VAR_INT.decode(buf),
                SONGS.decode(buf), MuseCodec.STRING.decode(buf))));

        @Override
        public PayloadType<SearchResultS2C> type() {
            return TYPE;
        }
    }

    /** Upcoming songs, excluding the one currently playing. */
    public record QueueS2C(List<QueueEntry> queue) implements Payload {
        public static final PayloadType<QueueS2C> TYPE = new PayloadType<>("queue", MuseCodecs.QUEUE_ENTRY.list().map(QueueS2C::new, QueueS2C::queue));

        @Override
        public PayloadType<QueueS2C> type() {
            return TYPE;
        }
    }

    /** Start playing entry from elapsedMs (non-zero for players who join mid-song). translation is an LRC too, "" if none. */
    public record PlayS2C(QueueEntry entry, String url, long elapsedMs, String lyric, String translation) implements Payload {
        public static final PayloadType<PlayS2C> TYPE = new PayloadType<>("play", MuseCodec.of((buf, p) -> {
            MuseCodecs.QUEUE_ENTRY.encode(buf, p.entry());
            MuseCodec.STRING.encode(buf, p.url());
            MuseCodec.VAR_LONG.encode(buf, p.elapsedMs());
            MuseCodec.STRING.encode(buf, p.lyric());
            MuseCodec.STRING.encode(buf, p.translation());
        }, buf -> new PlayS2C(MuseCodecs.QUEUE_ENTRY.decode(buf), MuseCodec.STRING.decode(buf), MuseCodec.VAR_LONG.decode(buf),
                MuseCodec.STRING.decode(buf), MuseCodec.STRING.decode(buf))));

        @Override
        public PayloadType<PlayS2C> type() {
            return TYPE;
        }
    }

    public record StopS2C() implements Payload {
        public static final StopS2C INSTANCE = new StopS2C();
        public static final PayloadType<StopS2C> TYPE = new PayloadType<>("stop", MuseCodec.unit(INSTANCE));

        @Override
        public PayloadType<StopS2C> type() {
            return TYPE;
        }
    }

    /**
     * One platform's account. qrContent is non-empty while its QR login is in progress; loginState is a
     * human-readable status line. Both are only sent to players who may manage the accounts.
     */
    public record AccountInfo(Platform platform, boolean loggedIn, String nickname, boolean vip, String qrContent, String loginState) {
        public static final MuseCodec<AccountInfo> CODEC = MuseCodec.of((buf, a) -> {
            MuseCodecs.PLATFORM.encode(buf, a.platform());
            MuseCodec.BOOL.encode(buf, a.loggedIn());
            MuseCodec.STRING.encode(buf, a.nickname());
            MuseCodec.BOOL.encode(buf, a.vip());
            MuseCodec.STRING.encode(buf, a.qrContent());
            MuseCodec.STRING.encode(buf, a.loginState());
        }, buf -> new AccountInfo(MuseCodecs.PLATFORM.decode(buf), MuseCodec.BOOL.decode(buf), MuseCodec.STRING.decode(buf),
                MuseCodec.BOOL.decode(buf), MuseCodec.STRING.decode(buf), MuseCodec.STRING.decode(buf)));
    }

    /** Every platform's account; canManage tells the client to show the account page. */
    public record AccountS2C(boolean canManage, List<AccountInfo> accounts) implements Payload {
        private static final MuseCodec<List<AccountInfo>> ACCOUNTS = AccountInfo.CODEC.list();
        public static final PayloadType<AccountS2C> TYPE = new PayloadType<>("account", MuseCodec.of((buf, p) -> {
            MuseCodec.BOOL.encode(buf, p.canManage());
            ACCOUNTS.encode(buf, p.accounts());
        }, buf -> new AccountS2C(MuseCodec.BOOL.decode(buf), ACCOUNTS.decode(buf))));

        @Override
        public PayloadType<AccountS2C> type() {
            return TYPE;
        }
    }

    /** In registration order. Loaders register every type here; handlers are in PacketHandlers and ClientHandlers. */
    public static final List<PayloadType<?>> TO_SERVER = List.of(
            HelloC2S.TYPE, SearchC2S.TYPE, RequestSongC2S.TYPE, SkipC2S.TYPE, RemoveC2S.TYPE, LoginStartC2S.TYPE, LogoutC2S.TYPE);
    public static final List<PayloadType<?>> TO_CLIENT = List.of(
            SearchResultS2C.TYPE, QueueS2C.TYPE, PlayS2C.TYPE, StopS2C.TYPE, AccountS2C.TYPE);
}
