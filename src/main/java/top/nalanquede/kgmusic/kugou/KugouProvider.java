package top.nalanquede.kgmusic.kugou;

import java.nio.file.Path;
import java.util.List;

import top.nalanquede.kgmusic.KgMusic;
import top.nalanquede.kgmusic.common.LoginMethod;
import top.nalanquede.kgmusic.common.Platform;
import top.nalanquede.kgmusic.common.Song;
import top.nalanquede.kgmusic.music.MusicProvider;

/** {@link MusicProvider} on top of {@link KugouApi}; the account lives in config/kgmusic/account.json. */
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
            KgMusic.LOGGER.info("KuGou device registered, dfid={}", api.account().dfid);
        } catch (KugouException e) {
            KgMusic.LOGGER.warn("KuGou device registration failed: {}", e.getMessage());
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
