package top.nalanquede.jukeboxmuse.kugou;

import java.nio.file.Path;
import java.util.List;

import top.nalanquede.jukeboxmuse.common.LoginMethod;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.Song;
import top.nalanquede.jukeboxmuse.music.MusicLog;
import top.nalanquede.jukeboxmuse.music.MusicProvider;

/** {@link MusicProvider} on top of {@link KugouApi}; the account lives in config/jukeboxmuse/kugou.json. */
public final class KugouProvider implements MusicProvider {
    private final KugouApi api;

    public KugouProvider(Path file) {
        this.api = new KugouApi(KugouAccount.load(file));
    }

    @Override
    public Platform platform() {
        return Platform.KUGOU;
    }

    @Override
    public boolean isLoggedIn() {
        return api.account().isLoggedIn();
    }

    @Override
    public String nickname() {
        return api.account().nickname;
    }

    @Override
    public boolean vip() {
        return api.account().vipType > 0;
    }

    /** song_url and search need a registered dfid. */
    @Override
    public void prepare() {
        if (api.account().hasDfid()) return;
        try {
            api.registerDev();
            MusicLog.LOGGER.info("KuGou device registered, dfid={}", api.account().dfid);
        } catch (KugouException e) {
            MusicLog.LOGGER.warn("KuGou device registration failed: {}", e.getMessage());
        }
    }

    @Override
    public List<Song> search(String keyword, int page, int pageSize) throws KugouException {
        return api.search(keyword, page, pageSize);
    }

    @Override
    public String songUrl(Song song, String quality) throws KugouException {
        return api.songUrl(song, quality);
    }

    @Override
    public LyricText lyric(Song song) throws KugouException {
        return api.lyric(song);
    }

    @Override
    public LoginQr loginQr(LoginMethod method) throws KugouException {
        String key = api.qrKey();
        return new LoginQr(method, key, KugouApi.qrContent(key));
    }

    @Override
    public LoginStatus loginCheck(LoginQr qr) throws KugouException {
        return switch (api.qrCheck(qr.key())) {
            case 0 -> LoginStatus.EXPIRED;
            case 2 -> LoginStatus.SCANNED;
            case 4 -> LoginStatus.SUCCESS;
            default -> LoginStatus.WAITING;
        };
    }

    @Override
    public long lastRefreshMs() {
        return api.account().lastRefreshMs;
    }

    @Override
    public void refresh() throws KugouException {
        api.refreshToken();
    }

    @Override
    public void logout() {
        api.account().logout();
    }
}
