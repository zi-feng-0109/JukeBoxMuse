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

    public static void handle(Payload payload, ServerPlayer player) {
        MusicService s = MusicService.get();
        if (s == null || player == null) return;
        if (payload instanceof HelloC2S) s.sendState(player);
        else if (payload instanceof SearchC2S p) s.search(player, p.platform(), p.keyword(), p.page());
        else if (payload instanceof RequestSongC2S p) s.request(player, p.song());
        else if (payload instanceof SkipC2S) s.skip(player.createCommandSourceStack());
        else if (payload instanceof RemoveC2S p) s.remove(player, p.index());
        else if (payload instanceof LoginStartC2S p) s.loginStart(player, p.method());
        else if (payload instanceof LogoutC2S p) s.logout(player, p.platform());
        else throw new IllegalArgumentException("Not a client→server payload: " + payload);
    }
}
