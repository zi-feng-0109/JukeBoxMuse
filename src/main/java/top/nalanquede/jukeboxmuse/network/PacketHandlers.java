package top.nalanquede.jukeboxmuse.network;

import net.minecraft.server.level.ServerPlayer;
import top.nalanquede.jukeboxmuse.network.MusePayloads.*;
import top.nalanquede.jukeboxmuse.server.MusicService;

/**
 * What each client→server packet does. The loaders register the payload types and call {@link #handle} on the
 * server thread; server→client packets go straight to ClientHandlers.
 */
public final class PacketHandlers {
    /** Bump whenever a payload changes so mismatched client and server versions refuse to connect. */
    public static final String PROTOCOL_VERSION = "1";

    private PacketHandlers() {
    }

    public static void handle(Object payload, ServerPlayer player) {
        MusicService s = MusicService.get();
        if (s == null || player == null) return;
        switch (payload) {
            case HelloC2S p -> s.sendState(player);
            case SearchC2S p -> s.search(player, p.platform(), p.keyword(), p.page());
            case RequestSongC2S p -> s.request(player, p.song());
            case SkipC2S p -> s.skip(player.createCommandSourceStack());
            case RemoveC2S p -> s.remove(player, p.index());
            case LoginStartC2S p -> s.loginStart(player, p.method());
            case LogoutC2S p -> s.logout(player, p.platform());
            default -> throw new IllegalArgumentException("Not a client→server payload: " + payload);
        }
    }
}
