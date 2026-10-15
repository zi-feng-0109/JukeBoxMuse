package top.nalanquede.jukeboxmuse.neoforge;

//? if neoforge {
/*import net.minecraft.server.level.ServerPlayer;
import top.nalanquede.jukeboxmuse.client.ClientHandlers;
import top.nalanquede.jukeboxmuse.network.McPacket;
import top.nalanquede.jukeboxmuse.network.MusePayloads;
import top.nalanquede.jukeboxmuse.network.PacketHandlers;
import top.nalanquede.jukeboxmuse.network.PayloadType;
//? if >=1.20.5 {
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//?} else {
/^import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
^///?}

/^* Registers the payloads with NeoForge. Handlers run on the main thread. ^/
final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    //? if >=1.20.5 {
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(PacketHandlers.PROTOCOL_VERSION);
        for (PayloadType<?> type : MusePayloads.TO_SERVER) {
            r.playToServer(type.mcType(), type.streamCodec(),
                    (p, ctx) -> PacketHandlers.handle(p.payload(), ctx.player() instanceof ServerPlayer sp ? sp : null));
        }
        // The lambda keeps ClientHandlers from being loaded on a dedicated server
        for (PayloadType<?> type : MusePayloads.TO_CLIENT) {
            r.playToClient(type.mcType(), type.streamCodec(), (p, ctx) -> ClientHandlers.handle(p.payload()));
        }
    }
    //?} else {
    /^// 20.4 runs handlers on the network thread; workHandler() moves them to the main thread
    static void register(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar r = event.registrar(top.nalanquede.jukeboxmuse.JukeboxMuse.MODID).versioned(PacketHandlers.PROTOCOL_VERSION);
        for (PayloadType<?> type : MusePayloads.TO_SERVER) {
            r.play(type.id(), buf -> new McPacket(type.decode(buf)), h -> h.server((p, ctx) -> ctx.workHandler().execute(
                    () -> PacketHandlers.handle(p.payload(), ctx.player().orElse(null) instanceof ServerPlayer sp ? sp : null))));
        }
        for (PayloadType<?> type : MusePayloads.TO_CLIENT) {
            r.play(type.id(), buf -> new McPacket(type.decode(buf)), h -> h.client((p, ctx) -> ctx.workHandler().execute(
                    () -> ClientHandlers.handle(p.payload()))));
        }
    }
    ^///?}
}
*///?}
