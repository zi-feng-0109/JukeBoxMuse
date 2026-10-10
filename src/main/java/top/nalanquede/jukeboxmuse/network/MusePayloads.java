package top.nalanquede.jukeboxmuse.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.common.LoginMethod;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.QueueEntry;
import top.nalanquede.jukeboxmuse.common.Song;

/** Every packet the mod sends. C2S = client to server, S2C = server to client. */
public final class MusePayloads {
    private MusePayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JukeboxMuse.MODID, name));
    }

    // ---------- client -> server ----------

    /** Sent when the music screen opens, so the server replies with queue + account state. */
    public record HelloC2S() implements CustomPacketPayload {
        public static final HelloC2S INSTANCE = new HelloC2S();
        public static final Type<HelloC2S> TYPE = typeOf("hello");
        public static final StreamCodec<ByteBuf, HelloC2S> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<HelloC2S> type() {
            return TYPE;
        }
    }

    public record SearchC2S(Platform platform, String keyword, int page) implements CustomPacketPayload {
        public static final Type<SearchC2S> TYPE = typeOf("search");
        public static final StreamCodec<ByteBuf, SearchC2S> STREAM_CODEC = StreamCodec.composite(
                Platform.STREAM_CODEC, SearchC2S::platform,
                ByteBufCodecs.stringUtf8(64), SearchC2S::keyword,
                ByteBufCodecs.VAR_INT, SearchC2S::page,
                SearchC2S::new);

        @Override
        public Type<SearchC2S> type() {
            return TYPE;
        }
    }

    public record RequestSongC2S(Song song) implements CustomPacketPayload {
        public static final Type<RequestSongC2S> TYPE = typeOf("request_song");
        public static final StreamCodec<ByteBuf, RequestSongC2S> STREAM_CODEC = Song.STREAM_CODEC.map(RequestSongC2S::new, RequestSongC2S::song);

        @Override
        public Type<RequestSongC2S> type() {
            return TYPE;
        }
    }

    public record SkipC2S() implements CustomPacketPayload {
        public static final SkipC2S INSTANCE = new SkipC2S();
        public static final Type<SkipC2S> TYPE = typeOf("skip");
        public static final StreamCodec<ByteBuf, SkipC2S> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<SkipC2S> type() {
            return TYPE;
        }
    }

    /** Remove the queue entry at index (0 = next song after the current one). */
    public record RemoveC2S(int index) implements CustomPacketPayload {
        public static final Type<RemoveC2S> TYPE = typeOf("remove");
        public static final StreamCodec<ByteBuf, RemoveC2S> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(RemoveC2S::new, RemoveC2S::index);

        @Override
        public Type<RemoveC2S> type() {
            return TYPE;
        }
    }

    /** Start (or re-show) a QR login from the account page. */
    public record LoginStartC2S(LoginMethod method) implements CustomPacketPayload {
        public static final Type<LoginStartC2S> TYPE = typeOf("login_start");
        public static final StreamCodec<ByteBuf, LoginStartC2S> STREAM_CODEC = LoginMethod.STREAM_CODEC.map(LoginStartC2S::new, LoginStartC2S::method);

        @Override
        public Type<LoginStartC2S> type() {
            return TYPE;
        }
    }

    public record LogoutC2S(Platform platform) implements CustomPacketPayload {
        public static final Type<LogoutC2S> TYPE = typeOf("logout");
        public static final StreamCodec<ByteBuf, LogoutC2S> STREAM_CODEC = Platform.STREAM_CODEC.map(LogoutC2S::new, LogoutC2S::platform);

        @Override
        public Type<LogoutC2S> type() {
            return TYPE;
        }
    }

    // ---------- server -> client ----------

    /** error is empty on success. */
    public record SearchResultS2C(Platform platform, String keyword, int page, List<Song> songs, String error) implements CustomPacketPayload {
        public static final Type<SearchResultS2C> TYPE = typeOf("search_result");
        public static final StreamCodec<ByteBuf, SearchResultS2C> STREAM_CODEC = StreamCodec.composite(
                Platform.STREAM_CODEC, SearchResultS2C::platform,
                ByteBufCodecs.STRING_UTF8, SearchResultS2C::keyword,
                ByteBufCodecs.VAR_INT, SearchResultS2C::page,
                Song.STREAM_CODEC.apply(ByteBufCodecs.list()), SearchResultS2C::songs,
                ByteBufCodecs.STRING_UTF8, SearchResultS2C::error,
                SearchResultS2C::new);

        @Override
        public Type<SearchResultS2C> type() {
            return TYPE;
        }
    }

    /** Upcoming songs, excluding the one currently playing. */
    public record QueueS2C(List<QueueEntry> queue) implements CustomPacketPayload {
        public static final Type<QueueS2C> TYPE = typeOf("queue");
        public static final StreamCodec<ByteBuf, QueueS2C> STREAM_CODEC = QueueEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(QueueS2C::new, QueueS2C::queue);

        @Override
        public Type<QueueS2C> type() {
            return TYPE;
        }
    }

    /** Start playing entry from elapsedMs (non-zero for players who join mid-song). translation is an LRC too, "" if none. */
    public record PlayS2C(QueueEntry entry, String url, long elapsedMs, String lyric, String translation) implements CustomPacketPayload {
        public static final Type<PlayS2C> TYPE = typeOf("play");
        public static final StreamCodec<ByteBuf, PlayS2C> STREAM_CODEC = StreamCodec.composite(
                QueueEntry.STREAM_CODEC, PlayS2C::entry,
                ByteBufCodecs.STRING_UTF8, PlayS2C::url,
                ByteBufCodecs.VAR_LONG, PlayS2C::elapsedMs,
                ByteBufCodecs.STRING_UTF8, PlayS2C::lyric,
                ByteBufCodecs.STRING_UTF8, PlayS2C::translation,
                PlayS2C::new);

        @Override
        public Type<PlayS2C> type() {
            return TYPE;
        }
    }

    public record StopS2C() implements CustomPacketPayload {
        public static final StopS2C INSTANCE = new StopS2C();
        public static final Type<StopS2C> TYPE = typeOf("stop");
        public static final StreamCodec<ByteBuf, StopS2C> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<StopS2C> type() {
            return TYPE;
        }
    }

    /**
     * One platform's account. qrContent is non-empty while its QR login is in progress; loginState is a
     * human-readable status line. Both are only sent to players who may manage the accounts.
     */
    public record AccountInfo(Platform platform, boolean loggedIn, String nickname, boolean vip, String qrContent, String loginState) {
        public static final StreamCodec<ByteBuf, AccountInfo> STREAM_CODEC = StreamCodec.composite(
                Platform.STREAM_CODEC, AccountInfo::platform,
                ByteBufCodecs.BOOL, AccountInfo::loggedIn,
                ByteBufCodecs.STRING_UTF8, AccountInfo::nickname,
                ByteBufCodecs.BOOL, AccountInfo::vip,
                ByteBufCodecs.STRING_UTF8, AccountInfo::qrContent,
                ByteBufCodecs.STRING_UTF8, AccountInfo::loginState,
                AccountInfo::new);
    }

    /** Every platform's account; canManage tells the client to show the account page. */
    public record AccountS2C(boolean canManage, List<AccountInfo> accounts) implements CustomPacketPayload {
        public static final Type<AccountS2C> TYPE = typeOf("account");
        public static final StreamCodec<ByteBuf, AccountS2C> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, AccountS2C::canManage,
                AccountInfo.STREAM_CODEC.apply(ByteBufCodecs.list()), AccountS2C::accounts,
                AccountS2C::new);

        @Override
        public Type<AccountS2C> type() {
            return TYPE;
        }
    }
}
