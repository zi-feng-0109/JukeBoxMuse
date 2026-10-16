package top.nalanquede.jukeboxmuse.fabric;

//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.nalanquede.jukeboxmuse.network.MusePayloads;
import top.nalanquede.jukeboxmuse.network.PacketHandlers;
import top.nalanquede.jukeboxmuse.network.PayloadType;
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import top.nalanquede.jukeboxmuse.network.MusePayloads.Payload;
*///?}

/**
 * The payloads on Fabric's networking API. From 1.20.5 they are registered payload types; before, plain channels
 * carrying a byte buffer, whose receivers run on the network thread.
 */
final class FabricNetwork {
    private FabricNetwork() {
    }

    /** Both sides: the payload types, and the server's receivers (which run on the server thread). */
    static void register() {
        //? if >=1.20.5 {
        for (PayloadType<?> type : MusePayloads.TO_SERVER) {
            PayloadTypeRegistry./*? if >=26.1 {*//*serverboundPlay*//*?} else {*/playC2S/*?}*/().register(type.mcType(), type.streamCodec());
            ServerPlayNetworking.registerGlobalReceiver(type.mcType(), (p, ctx) -> PacketHandlers.handle(p.payload(), ctx.player()));
        }
        // Their receivers are registered by the client entry point
        for (PayloadType<?> type : MusePayloads.TO_CLIENT) {
            PayloadTypeRegistry./*? if >=26.1 {*//*clientboundPlay*//*?} else {*/playS2C/*?}*/().register(type.mcType(), type.streamCodec());
        }
        //?} else {
        /*for (PayloadType<?> type : MusePayloads.TO_SERVER) {
            ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, handler, buf, sender) -> {
                // The buffer is released once this returns, so decode now and handle on the server thread
                Payload payload = type.decode(buf);
                server.execute(() -> PacketHandlers.handle(payload, player));
            });
        }
        *///?}
    }

    //? if <1.20.5 {
    /*static FriendlyByteBuf write(Payload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.type().encode(buf, payload);
        return buf;
    }
    *///?}
}
//?}
