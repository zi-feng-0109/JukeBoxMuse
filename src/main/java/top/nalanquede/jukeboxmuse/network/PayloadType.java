package top.nalanquede.jukeboxmuse.network;

import io.netty.buffer.ByteBuf;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.network.MusePayloads.Payload;

/** One kind of packet: its channel (jukeboxmuse:name) and how its payload is written. */
public final class PayloadType<T extends Payload> {
    private final String name;
    private final MuseCodec<T> codec;
    private ResourceLocation id;
    //? if >=1.20.5 {
    private CustomPacketPayload.Type<McPacket> mcType;
    //?}

    PayloadType(String name, MuseCodec<T> codec) {
        this.name = name;
        this.codec = codec;
    }

    /** The channel. Created on first use: payload classes load before Minecraft is ready for identifiers. */
    public ResourceLocation id() {
        if (id == null) id = JukeboxMuse.id(name);
        return id;
    }

    public T decode(ByteBuf buf) {
        return codec.decode(buf);
    }

    /** Writes payload, which must be of this type. */
    @SuppressWarnings("unchecked")
    public void encode(ByteBuf buf, Payload payload) {
        codec.encode(buf, (T) payload);
    }

    //? if >=1.20.5 {
    public CustomPacketPayload.Type<McPacket> mcType() {
        if (mcType == null) mcType = new CustomPacketPayload.Type<>(id());
        return mcType;
    }

    public StreamCodec<ByteBuf, McPacket> streamCodec() {
        return StreamCodec.of((buf, packet) -> encode(buf, packet.payload()), buf -> new McPacket(decode(buf)));
    }
    //?}

    @Override
    public String toString() {
        return name;
    }
}
