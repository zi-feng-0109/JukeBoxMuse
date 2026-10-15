package top.nalanquede.jukeboxmuse.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

/**
 * Reads and writes one value on a packet buffer. The mod's own stand-in for Minecraft's StreamCodec, which only exists
 * from 1.20.5, so the payloads are the same on every version. The wire format is Minecraft's (VarInt, VarLong,
 * VarInt-prefixed UTF-8 strings and lists), so jars built with StreamCodec still talk to these.
 */
public interface MuseCodec<T> {
    void encode(ByteBuf buf, T value);

    T decode(ByteBuf buf);

    static <T> MuseCodec<T> of(BiConsumer<ByteBuf, T> encoder, Function<ByteBuf, T> decoder) {
        return new MuseCodec<>() {
            @Override
            public void encode(ByteBuf buf, T value) {
                encoder.accept(buf, value);
            }

            @Override
            public T decode(ByteBuf buf) {
                return decoder.apply(buf);
            }
        };
    }

    /** Writes nothing; always decodes to value. */
    static <T> MuseCodec<T> unit(T value) {
        return of((buf, v) -> {
        }, buf -> value);
    }

    default <R> MuseCodec<R> map(Function<T, R> to, Function<R, T> from) {
        return of((buf, r) -> encode(buf, from.apply(r)), buf -> to.apply(decode(buf)));
    }

    /** A VarInt count, then the elements. */
    default MuseCodec<List<T>> list() {
        return of((buf, list) -> {
            writeVarInt(buf, list.size());
            for (T t : list) encode(buf, t);
        }, buf -> {
            int n = readVarInt(buf);
            // Every element takes at least a byte, so a bigger count is garbage, not a huge allocation
            if (n < 0 || n > buf.readableBytes()) throw new DecoderException("Bad list size " + n);
            List<T> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(decode(buf));
            return list;
        });
    }

    MuseCodec<Boolean> BOOL = of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    MuseCodec<Integer> VAR_INT = of(MuseCodec::writeVarInt, MuseCodec::readVarInt);
    MuseCodec<Long> VAR_LONG = of(MuseCodec::writeVarLong, MuseCodec::readVarLong);
    MuseCodec<Long> LONG = of(ByteBuf::writeLong, ByteBuf::readLong);
    MuseCodec<String> STRING = string(32767);

    /** At most maxLength UTF-16 chars, like Minecraft's strings. */
    static MuseCodec<String> string(int maxLength) {
        return of((buf, s) -> writeUtf(buf, s, maxLength), buf -> readUtf(buf, maxLength));
    }

    /** An enum by ordinal, so new constants must only be appended. */
    static <E extends Enum<E>> MuseCodec<E> ordinal(E[] values) {
        return of((buf, e) -> writeVarInt(buf, e.ordinal()), buf -> {
            int i = readVarInt(buf);
            if (i < 0 || i >= values.length) throw new DecoderException("Bad ordinal " + i);
            return values[i];
        });
    }

    static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    static int readVarInt(ByteBuf buf) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte b = buf.readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new DecoderException("VarInt too big");
    }

    static void writeVarLong(ByteBuf buf, long value) {
        while ((value & ~0x7FL) != 0) {
            buf.writeByte((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte((int) value);
    }

    static long readVarLong(ByteBuf buf) {
        long value = 0;
        for (int shift = 0; shift < 70; shift += 7) {
            byte b = buf.readByte();
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new DecoderException("VarLong too big");
    }

    private static void writeUtf(ByteBuf buf, String s, int maxLength) {
        if (s.length() > maxLength) throw new EncoderException("String too long (" + s.length() + " > " + maxLength + ")");
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxLength * 3) throw new EncoderException("String too long (" + bytes.length + " bytes)");
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf, int maxLength) {
        int bytes = readVarInt(buf);
        if (bytes < 0 || bytes > maxLength * 3) throw new DecoderException("String too long (" + bytes + " bytes)");
        if (bytes > buf.readableBytes()) throw new DecoderException("String longer than the packet");
        String s = buf.toString(buf.readerIndex(), bytes, StandardCharsets.UTF_8);
        buf.skipBytes(bytes);
        if (s.length() > maxLength) throw new DecoderException("String too long (" + s.length() + " > " + maxLength + ")");
        return s;
    }
}
