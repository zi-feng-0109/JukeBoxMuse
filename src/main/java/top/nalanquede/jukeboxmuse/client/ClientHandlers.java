package top.nalanquede.jukeboxmuse.client;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.network.MusePayloads.*;

/** Handlers for server→client packets. Run on the client main thread. */
public final class ClientHandlers {
    private ClientHandlers() {
    }

    public static void onSearchResult(SearchResultS2C p) {
        ClientState.searchPlatform = p.platform();
        ClientState.searchKeyword = p.keyword();
        ClientState.searchPage = p.page();
        ClientState.searchResults = p.songs();
        ClientState.searchError = p.error();
        ClientState.searching = false;
    }

    public static void onQueue(QueueS2C p) {
        ClientState.queue = p.queue();
        ClientState.clearPending();
    }

    public static void onPlay(PlayS2C p) {
        // The server resends the current song whenever the screen opens (HelloC2S → sendState);
        // restarting the stream then would cut out the music while it re-downloads and seeks.
        boolean alreadyPlaying = p.entry().equals(ClientState.nowPlaying) && AudioPlayer.isPlaying(p.url());
        ClientState.nowPlaying = p.entry();
        ClientState.lyrics = Lyrics.parse(p.lyric(), p.translation());
        ClientState.startedAtMs = System.currentTimeMillis() - p.elapsedMs();
        if (!alreadyPlaying) {
            AudioPlayer.play(p.url(), p.elapsedMs());
            // The rainbow "Now Playing" line a jukebox shows above the hotbar
            Minecraft.getInstance().gui.setNowPlaying(Component.literal(p.entry().song().title()));
            CoverArt.texture(p.entry().song().coverUrl());
        }
    }

    public static void onStop() {
        ClientState.nowPlaying = null;
        ClientState.lyrics = Lyrics.EMPTY;
        AudioPlayer.stop();
    }

    public static void onAccount(AccountS2C p) {
        Map<Platform, AccountInfo> accounts = new EnumMap<>(Platform.class);
        String qr = "";
        for (AccountInfo a : p.accounts()) {
            accounts.put(a.platform(), a);
            if (!a.qrContent().isEmpty()) qr = a.qrContent();
        }
        ClientState.canManage = p.canManage();
        ClientState.accounts = accounts;
        // The server runs at most one QR login at a time
        QrCode.update(qr);
    }
}
