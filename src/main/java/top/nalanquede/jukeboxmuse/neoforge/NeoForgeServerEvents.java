package top.nalanquede.jukeboxmuse.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.server.MuseCommands;
import top.nalanquede.jukeboxmuse.server.MusicService;

@EventBusSubscriber(modid = JukeboxMuse.MODID)
final class NeoForgeServerEvents {
    private NeoForgeServerEvents() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MusicService.start(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        MusicService.stop();
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MusicService service = MusicService.get();
        if (service != null) service.tick();
    }

    @SubscribeEvent
    static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        MusicService service = MusicService.get();
        if (service != null && event.getEntity() instanceof ServerPlayer player) service.sendState(player);
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        MuseCommands.register(event.getDispatcher());
    }
}
