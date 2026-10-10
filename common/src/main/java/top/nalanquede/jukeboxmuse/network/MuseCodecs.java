package top.nalanquede.jukeboxmuse.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import top.nalanquede.jukeboxmuse.common.LoginMethod;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.QueueEntry;
import top.nalanquede.jukeboxmuse.common.Song;

/** Network codecs for the core data types, which know nothing about Minecraft themselves. */
public final class MuseCodecs {
    private MuseCodecs() {
    }

    /** By ordinal, so new platforms and login methods must only be appended. */
    public static final StreamCodec<ByteBuf, Platform> PLATFORM = ByteBufCodecs.idMapper(i -> Platform.values()[i], Platform::ordinal);
    public static final StreamCodec<ByteBuf, LoginMethod> LOGIN_METHOD = ByteBufCodecs.idMapper(i -> LoginMethod.values()[i], LoginMethod::ordinal);

    public static final StreamCodec<ByteBuf, Song> SONG = StreamCodec.of(MuseCodecs::writeSong, MuseCodecs::readSong);

    public static final StreamCodec<ByteBuf, QueueEntry> QUEUE_ENTRY = StreamCodec.composite(
            SONG, QueueEntry::song,
            ByteBufCodecs.STRING_UTF8, QueueEntry::requester,
            QueueEntry::new);

    private static void writeSong(ByteBuf buf, Song s) {
        PLATFORM.encode(buf, s.platform());
        ByteBufCodecs.STRING_UTF8.encode(buf, s.id());
        ByteBufCodecs.STRING_UTF8.encode(buf, s.fileId());
        buf.writeLong(s.albumId());
        buf.writeLong(s.trackId());
        ByteBufCodecs.STRING_UTF8.encode(buf, s.name());
        ByteBufCodecs.STRING_UTF8.encode(buf, s.singer());
        ByteBufCodecs.STRING_UTF8.encode(buf, s.album());
        buf.writeLong(s.durationMs());
        buf.writeBoolean(s.vip());
        ByteBufCodecs.STRING_UTF8.encode(buf, s.coverUrl());
    }

    private static Song readSong(ByteBuf buf) {
        return new Song(PLATFORM.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readLong(), buf.readLong(),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readLong(), buf.readBoolean(), ByteBufCodecs.STRING_UTF8.decode(buf));
    }
}
