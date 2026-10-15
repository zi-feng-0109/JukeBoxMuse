package top.nalanquede.jukeboxmuse.platform;

import java.nio.file.Path;

import net.minecraft.server.level.ServerPlayer;
import top.nalanquede.jukeboxmuse.network.MusePayloads.Payload;

/**
 * What shared code needs from the mod loader. Each loader's entry point installs its implementation with
 * {@link #install} before anything else runs.
 */
public interface LoaderServices {
    /** The game's config directory (config/). */
    Path configDir();

    void sendToServer(Payload payload);

    /** Does nothing for a player without the mod (vanilla clients may join a Fabric server). */
    void sendToPlayer(ServerPlayer player, Payload payload);

    default void sendToAllPlayers(Payload payload) {
        net.minecraft.server.MinecraftServer server = top.nalanquede.jukeboxmuse.server.MusicService.server();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendToPlayer(player, payload);
    }

    static LoaderServices get() {
        LoaderServices s = Holder.instance;
        if (s == null) throw new IllegalStateException("No mod loader services installed");
        return s;
    }

    static void install(LoaderServices services) {
        Holder.instance = services;
    }

    final class Holder {
        private static volatile LoaderServices instance;

        private Holder() {
        }
    }
}
