package top.nalanquede.jukeboxmuse.network;

//? if >=1.20.2 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
//? if >=1.20.2 && <1.20.5 {
/*import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
*///?}
import top.nalanquede.jukeboxmuse.network.MusePayloads.Payload;

/** One of the mod's payloads as a Minecraft custom packet payload (1.20.2+), for the loaders that send those. */
public record McPacket(Payload payload) /*? if >=1.20.2 {*/implements CustomPacketPayload/*?}*/ {
    //? if >=1.20.5 {
    @Override
    public Type<McPacket> type() {
        return payload.type().mcType();
    }
    //?} elif >=1.20.2 {
    /*@Override
    public void write(FriendlyByteBuf buf) {
        payload.type().encode(buf, payload);
    }

    @Override
    public ResourceLocation id() {
        return payload.type().id();
    }
    *///?}
}
