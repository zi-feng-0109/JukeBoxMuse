package top.nalanquede.jukeboxmuse.platform;

import java.nio.file.Path;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * What shared code needs from the mod loader. Each loader's entry point installs its implementation with
 * {@link #install} before anything else runs.
 */
public interface LoaderServices {
    /** The game's config directory (config/). */
    Path configDir();

    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToAllPlayers(CustomPacketPayload payload);

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
