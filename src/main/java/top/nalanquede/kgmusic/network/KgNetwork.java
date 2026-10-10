package top.nalanquede.kgmusic.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import top.nalanquede.kgmusic.client.ClientHandlers;
import top.nalanquede.kgmusic.network.KgPayloads.*;
import top.nalanquede.kgmusic.server.MusicService;

public final class KgNetwork {
    private KgNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Version 2: multi-platform songs and accounts; 3: lyric translations; 4: album covers
        PayloadRegistrar r = event.registrar("4");

        r.playToServer(HelloC2S.TYPE, HelloC2S.STREAM_CODEC, (p, ctx) -> withService(ctx, (s, pl) -> s.sendState(pl)));
        r.playToServer(SearchC2S.TYPE, SearchC2S.STREAM_CODEC, (p, ctx) -> withService(ctx, (s, pl) -> s.search(pl, p.platform(), p.keyword(), p.page())));
        r.playToServer(RequestSongC2S.TYPE, RequestSongC2S.STREAM_CODEC, (p, ctx) -> withService(ctx, (s, pl) -> s.request(pl, p.song())));
        r.playToServer(SkipC2S.TYPE, SkipC2S.STREAM_CODEC, (p, ctx) -> withService(ctx, (s, pl) -> s.skip(pl.createCommandSourceStack())));
        r.playToServer(RemoveC2S.TYPE, RemoveC2S.STREAM_CODEC, (p, ctx) -> withService(ctx, (s, pl) -> s.remove(pl, p.index())));
        r.playToServer(LoginStartC2S.TYPE, LoginStartC2S.STREAM_CODEC, (p, ctx) -> withService(ctx, (s, pl) -> s.loginStart(pl, p.method())));
        r.playToServer(LogoutC2S.TYPE, LogoutC2S.STREAM_CODEC, (p, ctx) -> withService(ctx, (s, pl) -> s.logout(pl, p.platform())));

        // Lambdas keep ClientHandlers from being loaded on a dedicated server
        r.playToClient(SearchResultS2C.TYPE, SearchResultS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onSearchResult(p));
        r.playToClient(QueueS2C.TYPE, QueueS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onQueue(p));
        r.playToClient(PlayS2C.TYPE, PlayS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onPlay(p));
        r.playToClient(StopS2C.TYPE, StopS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onStop());
        r.playToClient(AccountS2C.TYPE, AccountS2C.STREAM_CODEC, (p, ctx) -> ClientHandlers.onAccount(p));
    }

    private interface ServiceAction {
        void run(MusicService service, ServerPlayer player);
    }

    private static void withService(IPayloadContext ctx, ServiceAction action) {
        MusicService service = MusicService.get();
        if (service != null && ctx.player() instanceof ServerPlayer player) {
            action.run(service, player);
        }
    }
}
