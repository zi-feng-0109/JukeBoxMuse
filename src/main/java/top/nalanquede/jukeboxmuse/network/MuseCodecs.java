package top.nalanquede.jukeboxmuse.network;

import top.nalanquede.jukeboxmuse.common.LoginMethod;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.QueueEntry;
import top.nalanquede.jukeboxmuse.common.Song;

/** Network codecs for the core data types, which know nothing about Minecraft themselves. */
public final class MuseCodecs {
    private MuseCodecs() {
    }

    /** By ordinal, so new platforms and login methods must only be appended. */
    public static final MuseCodec<Platform> PLATFORM = MuseCodec.ordinal(Platform.values());
    public static final MuseCodec<LoginMethod> LOGIN_METHOD = MuseCodec.ordinal(LoginMethod.values());
    /** Search keywords: what the search box allows. */
    public static final MuseCodec<String> KEYWORD = MuseCodec.string(64);

    public static final MuseCodec<Song> SONG = MuseCodec.of(MuseCodecs::writeSong, buf -> new Song(
            PLATFORM.decode(buf), MuseCodec.STRING.decode(buf), MuseCodec.STRING.decode(buf),
            MuseCodec.LONG.decode(buf), MuseCodec.LONG.decode(buf),
            MuseCodec.STRING.decode(buf), MuseCodec.STRING.decode(buf), MuseCodec.STRING.decode(buf),
            MuseCodec.LONG.decode(buf), MuseCodec.BOOL.decode(buf), MuseCodec.STRING.decode(buf)));

    public static final MuseCodec<QueueEntry> QUEUE_ENTRY = MuseCodec.of((buf, e) -> {
        SONG.encode(buf, e.song());
        MuseCodec.STRING.encode(buf, e.requester());
    }, buf -> new QueueEntry(SONG.decode(buf), MuseCodec.STRING.decode(buf)));

    private static void writeSong(io.netty.buffer.ByteBuf buf, Song s) {
        PLATFORM.encode(buf, s.platform());
        MuseCodec.STRING.encode(buf, s.id());
        MuseCodec.STRING.encode(buf, s.fileId());
        MuseCodec.LONG.encode(buf, s.albumId());
        MuseCodec.LONG.encode(buf, s.trackId());
        MuseCodec.STRING.encode(buf, s.name());
        MuseCodec.STRING.encode(buf, s.singer());
        MuseCodec.STRING.encode(buf, s.album());
        MuseCodec.LONG.encode(buf, s.durationMs());
        MuseCodec.BOOL.encode(buf, s.vip());
        MuseCodec.STRING.encode(buf, s.coverUrl());
    }
}
