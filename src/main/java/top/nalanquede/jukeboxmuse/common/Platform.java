package top.nalanquede.jukeboxmuse.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The music services songs can come from. Sent over the network by ordinal, so only append new values. */
public enum Platform {
    KUGOU("kugou", "酷狗"),
    NETEASE("netease", "网易云"),
    QQ("qq", "QQ音乐");

    public static final StreamCodec<ByteBuf, Platform> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Platform::ordinal);

    private final String id;
    private final String displayName;

    Platform(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** Lowercase name used in commands and file names. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}
