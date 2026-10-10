package top.nalanquede.kgmusic.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A song as a platform's search returns it. id is what the platform keys the track by (KuGou hash, NetEase song id,
 * QQ songmid); fileId, albumId and trackId are the extra ids some platforms need for song_url and lyrics
 * (QQ media_mid; KuGou album_id and album_audio_id). coverUrl is a small album cover image, "" when unknown.
 */
public record Song(Platform platform, String id, String fileId, long albumId, long trackId,
                   String name, String singer, String album, long durationMs, boolean vip, String coverUrl) {
    public static final StreamCodec<ByteBuf, Song> STREAM_CODEC = StreamCodec.of(Song::write, Song::read);

    private static void write(ByteBuf buf, Song s) {
        Platform.STREAM_CODEC.encode(buf, s.platform);
        ByteBufCodecs.STRING_UTF8.encode(buf, s.id);
        ByteBufCodecs.STRING_UTF8.encode(buf, s.fileId);
        buf.writeLong(s.albumId);
        buf.writeLong(s.trackId);
        ByteBufCodecs.STRING_UTF8.encode(buf, s.name);
        ByteBufCodecs.STRING_UTF8.encode(buf, s.singer);
        ByteBufCodecs.STRING_UTF8.encode(buf, s.album);
        buf.writeLong(s.durationMs);
        buf.writeBoolean(s.vip);
        ByteBufCodecs.STRING_UTF8.encode(buf, s.coverUrl);
    }

    private static Song read(ByteBuf buf) {
        return new Song(Platform.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readLong(), buf.readLong(),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readLong(), buf.readBoolean(), ByteBufCodecs.STRING_UTF8.decode(buf));
    }

    public String title() {
        return singer.isEmpty() ? name : name + " - " + singer;
    }

    /** Same track on the same platform. */
    public boolean sameTrack(Song other) {
        return platform == other.platform && id.equalsIgnoreCase(other.id);
    }
}
