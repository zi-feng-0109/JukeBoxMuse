package top.nalanquede.kgmusic.netease;

import java.nio.file.Path;

import top.nalanquede.kgmusic.music.AccountFile;

/** The server-wide NetEase account, persisted to config/kgmusic/netease.json. musicU is the login cookie. */
public final class NeteaseAccount {
    public String deviceId = "";
    public String musicU = "";
    public String csrf = "";
    public long userId;
    public String nickname = "";
    public int vipType;
    public long lastRefreshMs;

    private transient Path file;

    public static NeteaseAccount load(Path file) {
        NeteaseAccount account = AccountFile.load(file, NeteaseAccount.class, NeteaseAccount::new);
        account.file = file;
        if (account.deviceId == null || account.deviceId.isEmpty()) {
            account.deviceId = NeteaseCrypto.deviceId();
            account.save();
        }
        return account;
    }

    public boolean isLoggedIn() {
        return musicU != null && !musicU.isEmpty();
    }

    public synchronized void logout() {
        musicU = "";
        csrf = "";
        userId = 0;
        nickname = "";
        vipType = 0;
        save();
    }

    public synchronized void save() {
        AccountFile.save(file, this);
    }
}
