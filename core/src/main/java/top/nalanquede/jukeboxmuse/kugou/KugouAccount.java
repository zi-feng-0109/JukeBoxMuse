package top.nalanquede.jukeboxmuse.kugou;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Device identity and login state for the single server-wide KuGou account, persisted to config/jukeboxmuse/kugou.json.
 */
public final class KugouAccount {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String guid;
    public String mid;
    public String dev;
    public String mac = "02:00:00:00:00:00";
    public String dfid = "";
    public String token = "";
    public long userid;
    public String nickname = "";
    public int vipType;
    public String t1 = "";
    public long lastRefreshMs;

    private transient Path file;

    public static KugouAccount load(Path file) {
        KugouAccount account = null;
        if (Files.exists(file)) {
            try {
                account = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), KugouAccount.class);
            } catch (Exception ignored) {
                // Corrupt file: fall through and create a fresh device identity
            }
        }
        if (account == null) account = new KugouAccount();
        account.file = file;
        if (account.guid == null || account.guid.isEmpty()) {
            account.guid = KugouCrypto.md5(KugouCrypto.guid());
            account.mid = KugouCrypto.calculateMid(account.guid);
            account.dev = KugouCrypto.randomString(10).toUpperCase();
            account.save();
        }
        return account;
    }

    public boolean isLoggedIn() {
        return token != null && !token.isEmpty() && userid != 0;
    }

    public boolean hasDfid() {
        return dfid != null && !dfid.isEmpty() && !dfid.equals("-");
    }

    public synchronized void logout() {
        token = "";
        userid = 0;
        nickname = "";
        vipType = 0;
        t1 = "";
        save();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save " + file, e);
        }
    }
}
