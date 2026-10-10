package top.nalanquede.jukeboxmuse.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A queued song and the name of the player who requested it. */
public record QueueEntry(Song song, String requester) {
    public static final StreamCodec<ByteBuf, QueueEntry> STREAM_CODEC = StreamCodec.composite(
            Song.STREAM_CODEC, QueueEntry::song,
            ByteBufCodecs.STRING_UTF8, QueueEntry::requester,
            QueueEntry::new);
}
