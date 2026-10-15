package top.nalanquede.jukeboxmuse.neoforge;

//? if neoforge {
/*import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import top.nalanquede.jukeboxmuse.client.ClientHandlers;
import top.nalanquede.jukeboxmuse.network.MusePayloads.*;
import top.nalanquede.jukeboxmuse.network.PacketHandlers;

/^* Registers the payloads with NeoForge. Handlers run on the main thread. ^/
final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(PacketHandlers.PROTOCOL_VERSION);

        toServer(r, HelloC2S.TYPE, HelloC2S.STREAM_CODEC);
        toServer(r, SearchC2S.TYPE, SearchC2S.STREAM_CODEC);
        toServer(r, RequestSongC2S.TYPE, RequestSongC2S.STREAM_CODEC);
        toServer(r, SkipC2S.TYPE, SkipC2S.STREAM_CODEC);
        toServer(r, RemoveC2S.TYPE, RemoveC2S.STREAM_CODEC);
        toServer(r, LoginStartC2S.TYPE, LoginStartC2S.STREAM_CODEC);
        toServer(r, LogoutC2S.TYPE, LogoutC2S.STREAM_CODEC);

        // Lambdas keep ClientHandlers from being loaded on a dedicated server
        r.playToClient(SearchResultS2C.TYPE, SearchResultS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onSearchResult(p));
        r.playToClient(QueueS2C.TYPE, QueueS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onQueue(p));
        r.playToClient(PlayS2C.TYPE, PlayS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onPlay(p));
        r.playToClient(StopS2C.TYPE, StopS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onStop());
        r.playToClient(AccountS2C.TYPE, AccountS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onAccount(p));
    }

    private static <T extends CustomPacketPayload> void toServer(PayloadRegistrar r, CustomPacketPayload.Type<T> type, StreamCodec<ByteBuf, T> codec) {
        r.playToServer(type, codec, (p, ctx) -> PacketHandlers.handle(p, ctx.player() instanceof ServerPlayer sp ? sp : null));
    }
}
*///?}
