package top.nalanquede.jukeboxmuse.fabric;

//? if fabric {
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.network.MusePayloads.*;
import top.nalanquede.jukeboxmuse.network.PacketHandlers;
import top.nalanquede.jukeboxmuse.server.MuseCommands;
import top.nalanquede.jukeboxmuse.server.MusicService;

/** Fabric entry point (both sides): payload registration and server events. */
public final class JukeboxMuseFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        JukeboxMuse.init(new FabricServices());

        toServer(HelloC2S.TYPE, HelloC2S.STREAM_CODEC);
        toServer(SearchC2S.TYPE, SearchC2S.STREAM_CODEC);
        toServer(RequestSongC2S.TYPE, RequestSongC2S.STREAM_CODEC);
        toServer(SkipC2S.TYPE, SkipC2S.STREAM_CODEC);
        toServer(RemoveC2S.TYPE, RemoveC2S.STREAM_CODEC);
        toServer(LoginStartC2S.TYPE, LoginStartC2S.STREAM_CODEC);
        toServer(LogoutC2S.TYPE, LogoutC2S.STREAM_CODEC);
        // Receivers for these are registered by the client entry point
        PayloadTypeRegistry.playS2C().register(SearchResultS2C.TYPE, SearchResultS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(QueueS2C.TYPE, QueueS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PlayS2C.TYPE, PlayS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StopS2C.TYPE, StopS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AccountS2C.TYPE, AccountS2C.STREAM_CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FabricServices.server = server;
            MusicService.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            MusicService.stop();
            FabricServices.server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            MusicService service = MusicService.get();
            if (service != null) service.tick();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MusicService service = MusicService.get();
            if (service != null) service.sendState(handler.getPlayer());
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MuseCommands.register(dispatcher));
    }

    /** Fabric runs play receivers on the server thread. */
    private static <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<ByteBuf, T> codec) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> PacketHandlers.handle(payload, context.player()));
    }
}
//?}
