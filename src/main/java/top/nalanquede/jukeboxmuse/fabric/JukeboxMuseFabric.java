package top.nalanquede.jukeboxmuse.fabric;

//? if fabric {
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.server.MuseCommands;
import top.nalanquede.jukeboxmuse.server.MusicService;

/** Fabric entry point (both sides): payload registration and server events. */
public final class JukeboxMuseFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        JukeboxMuse.init(new FabricServices());

        FabricNetwork.register();

        ServerLifecycleEvents.SERVER_STARTED.register(MusicService::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MusicService.stop());
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
}
//?}
