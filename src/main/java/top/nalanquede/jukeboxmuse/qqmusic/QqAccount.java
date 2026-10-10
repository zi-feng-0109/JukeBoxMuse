package top.nalanquede.jukeboxmuse.qqmusic;

import java.nio.file.Path;
import java.util.UUID;

import com.google.gson.JsonObject;

import top.nalanquede.jukeboxmuse.music.AccountFile;
import top.nalanquede.jukeboxmuse.music.Json;

/**
 * The server-wide QQ Music credential, persisted to config/jukeboxmuse/qqmusic.json. Field names follow QQMusicApi's
 * Credential; loginType is 2 for a QQ login and 1 for WeChat.
 */
public final class QqAccount {
    public String guid = "";
    public long musicid;
    public String musickey = "";
    public String strMusicid = "";
    public String openid = "";
    public String accessToken = "";
    public String refreshToken = "";
    public String refreshKey = "";
    public String unionid = "";
    public long expiredAt;
    public int loginType;
    public String nickname = "";
    public long lastRefreshMs;

    private transient Path file;

    public static QqAccount load(Path file) {
        QqAccount account = AccountFile.load(file, QqAccount.class, QqAccount::new);
        account.file = file;
        if (account.guid == null || account.guid.isEmpty()) {
            account.guid = UUID.randomUUID().toString().replace("-", "");
            account.save();
        }
        return account;
    }

    public boolean isLoggedIn() {
        return musicid != 0 && musickey != null && !musickey.isEmpty();
    }

    public String uin() {
        return strMusicid != null && !strMusicid.isEmpty() ? strMusicid : String.valueOf(musicid);
    }

    /** Copies a music.login.LoginServer / QQConnectLogin response; empty fields keep their old value. */
    public synchronized void update(JsonObject data) {
        musicid = Json.num(data, "musicid", musicid);
        musickey = Json.str(data, "musickey", musickey);
        strMusicid = Json.str(data, "str_musicid", strMusicid);
        openid = Json.str(data, "openid", openid);
        accessToken = Json.str(data, "access_token", accessToken);
        refreshToken = Json.str(data, "refresh_token", refreshToken);
        refreshKey = Json.str(data, "refresh_key", refreshKey);
        unionid = Json.str(data, "unionid", unionid);
        expiredAt = Json.num(data, "expired_at", expiredAt);
        // WeChat musickeys start with W_X
        loginType = (int) Json.num(data, "loginType", musickey.startsWith("W_X") ? 1 : 2);
        lastRefreshMs = System.currentTimeMillis();
        save();
    }

    public synchronized void logout() {
        musicid = 0;
        musickey = "";
        strMusicid = "";
        openid = "";
        accessToken = "";
        refreshToken = "";
        refreshKey = "";
        unionid = "";
        expiredAt = 0;
        loginType = 0;
        nickname = "";
        save();
    }

    public synchronized void save() {
        AccountFile.save(file, this);
    }
}
