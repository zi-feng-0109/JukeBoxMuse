package top.nalanquede.kgmusic.kugou;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import top.nalanquede.kgmusic.KgMusic;
import top.nalanquede.kgmusic.common.Platform;
import top.nalanquede.kgmusic.common.Song;
import top.nalanquede.kgmusic.music.Json;
import top.nalanquede.kgmusic.music.MusicProvider.LyricText;

/**
 * The KuGou endpoints the mod needs, each ported from the matching KuGouMusicApi module/*.js.
 * All calls block; callers run them off the server thread.
 */
public final class KugouApi {
    private static final String QR_PAGE = "https://h5.kugou.com/apps/loginQRCode/html/index.html";
    private static final String TOKEN_KEY = "90b8382a1bb4ccdcf063102053fd75b8";
    private static final String TOKEN_IV = "f063102053fd75b8";

    private final KugouAccount account;

    public KugouApi(KugouAccount account) {
        this.account = account;
    }

    public KugouAccount account() {
        return account;
    }

    /** register_dev.js: obtains the dfid that song_url and search require. */
    public void registerDev() throws KugouException {
        JsonObject dev = new JsonObject();
        dev.addProperty("availableRamSize", 4983533568L);
        dev.addProperty("availableRomSize", 48114719);
        dev.addProperty("availableSDSize", 48114717);
        dev.addProperty("basebandVer", "");
        dev.addProperty("batteryLevel", 100);
        dev.addProperty("batteryStatus", 3);
        dev.addProperty("brand", "Redmi");
        dev.addProperty("buildSerial", "unknown");
        dev.addProperty("device", "marble");
        dev.addProperty("imei", account.guid);
        dev.addProperty("imsi", "");
        dev.addProperty("manufacturer", "Xiaomi");
        dev.addProperty("uuid", account.guid);
        for (String sensor : new String[]{"accelerometer", "gravity", "gyroscope", "light", "magnetic", "orientation", "pressure", "step_counter", "temperature"}) {
            dev.addProperty(sensor, false);
            dev.addProperty(sensor + "Value", "");
        }

        String aesKey = KugouCrypto.randomString(6).toLowerCase();
        JsonObject p = new JsonObject();
        p.addProperty("aes", aesKey);
        p.addProperty("uid", account.userid);
        p.addProperty("token", account.token == null ? "" : account.token);

        byte[] raw = KugouRequest.post("/risk/v2/r_register_dev")
                .base("https://userservice.kugou.com")
                .param("part", 1)
                .param("platid", 1)
                .param("p", KugouCrypto.rsaPkcs1Hex(p.toString()))
                .formBody(KugouCrypto.playlistEncryptBase64(dev.toString(), aesKey))
                .send(account);
        String text;
        try {
            text = KugouCrypto.playlistDecrypt(raw, aesKey);
        } catch (Exception e) {
            throw new KugouException("设备注册响应无法解密: " + new String(raw, StandardCharsets.UTF_8));
        }
        JsonObject body = JsonParser.parseString(text).getAsJsonObject();
        String dfid = Json.str(Json.obj(body, "data"), "dfid", "");
        if (Json.num(body, "status", 0) != 1 || dfid.isEmpty()) throw new KugouException("设备注册失败: " + text);
        account.dfid = dfid;
        account.save();
    }

    /** login_qr_key.js: returns the qrcode key. */
    public String qrKey() throws KugouException {
        JsonObject body = KugouRequest.get("/v2/qrcode")
                .base("https://login-user.kugou.com")
                .param("appid", 1001)
                .param("type", 1)
                .param("plat", 4)
                .param("qrcode_txt", QR_PAGE + "?appid=" + KugouRequest.APPID + "&")
                .param("srcappid", KugouRequest.SRCAPPID)
                .sign(KugouRequest.Sign.WEB)
                .json(account);
        String key = Json.str(Json.obj(body, "data"), "qrcode", "");
        if (key.isEmpty()) throw new KugouException("获取登录二维码失败: " + body);
        return key;
    }

    /** login_qr_create.js: the text the QR code must encode. */
    public static String qrContent(String key) {
        return QR_PAGE + "?qrcode=" + key;
    }

    /** login_qr_check.js status: 0 expired, 1 waiting, 2 scanned (awaiting confirm), 4 success. */
    public int qrCheck(String key) throws KugouException {
        JsonObject body = KugouRequest.get("/v2/get_userinfo_qrcode")
                .base("https://login-user.kugou.com")
                .param("plat", 4)
                .param("appid", KugouRequest.APPID)
                .param("srcappid", KugouRequest.SRCAPPID)
                .param("qrcode", key)
                .param("dev", account.dev)
                .sign(KugouRequest.Sign.WEB)
                .json(account);
        JsonObject data = Json.obj(body, "data");
        int status = (int) Json.num(data, "status", -1);
        if (status == 4) {
            account.token = Json.str(data, "token", "");
            account.userid = Json.num(data, "userid", 0);
            account.nickname = Json.str(data, "nickname", "");
            account.lastRefreshMs = System.currentTimeMillis();
            account.save();
        }
        return status;
    }

    /** login_token.js: extends the token's lifetime. */
    public void refreshToken() throws KugouException {
        if (!account.isLoggedIn()) throw new KugouException("未登录");
        long now = System.currentTimeMillis();

        JsonObject p3 = new JsonObject();
        p3.addProperty("clienttime", now / 1000);
        p3.addProperty("token", account.token);
        KugouCrypto.TempKeyCipher params = KugouCrypto.aesEncryptTempKey("{}");
        JsonObject pk = new JsonObject();
        pk.addProperty("clienttime_ms", now);
        pk.addProperty("key", params.tempKey());

        JsonObject data = new JsonObject();
        data.addProperty("dfid", account.hasDfid() ? account.dfid : "-");
        data.addProperty("p3", KugouCrypto.aesEncryptHex(p3.toString(), TOKEN_KEY, TOKEN_IV));
        data.addProperty("plat", 1);
        data.addProperty("t1", 0);
        data.addProperty("t2", 0);
        data.addProperty("t3", "MCwwLDAsMCwwLDAsMCwwLDA=");
        data.addProperty("pk", KugouCrypto.rsaRawHex(pk.toString()));
        data.addProperty("params", params.hex());
        data.addProperty("userid", String.valueOf(account.userid));
        data.addProperty("clienttime_ms", now);

        JsonObject body = KugouRequest.post("/v5/login_by_token")
                .base("http://login.user.kugou.com")
                .body(data.toString())
                .json(account);
        JsonObject result = Json.obj(body, "data");
        if (Json.num(body, "status", 0) != 1 || result == null) throw new KugouException("刷新登录失败: " + body);
        String secu = Json.str(result, "secu_params", "");
        if (!secu.isEmpty()) {
            String plain = KugouCrypto.aesDecryptTempKey(secu, params.tempKey());
            JsonElement parsed = null;
            try {
                parsed = JsonParser.parseString(plain);
            } catch (Exception ignored) {
                // Not JSON: the decrypted text is the token itself
            }
            if (parsed != null && parsed.isJsonObject()) {
                parsed.getAsJsonObject().entrySet().forEach(e -> result.add(e.getKey(), e.getValue()));
            } else {
                result.addProperty("token", plain);
            }
        }
        String token = Json.str(result, "token", "");
        if (!token.isEmpty()) account.token = token;
        long userid = Json.num(result, "userid", 0);
        if (userid != 0) account.userid = userid;
        account.t1 = Json.str(result, "t1", account.t1);
        account.vipType = (int) Json.num(result, "vip_type", account.vipType);
        account.nickname = Json.str(result, "nickname", account.nickname);
        account.lastRefreshMs = now;
        account.save();
    }

    /** search.js (type=song). Field names follow the AndroidFilter search response. */
    public List<Song> search(String keyword, int page, int pageSize) throws KugouException {
        JsonObject body = KugouRequest.get("/v2/search/song")
                .router("complexsearch.kugou.com")
                .param("keyword", keyword)
                .param("page", page)
                .param("pagesize", pageSize)
                .param("platform", "AndroidFilter")
                .param("iscorrection", 1)
                .param("privilegefilter", 0)
                .param("area_code", 1)
                .param("dopicfull", 1)
                .json(account);
        List<Song> songs = new ArrayList<>();
        for (JsonElement e : Json.arr(Json.obj(body, "data"), "lists")) {
            if (!e.isJsonObject()) continue;
            JsonObject s = e.getAsJsonObject();
            String hash = Json.str(s, "FileHash", "");
            if (hash.isEmpty()) continue;
            long privilege = Json.num(s, "Privilege", 0);
            long payType = Json.num(s, "PayType", 0);
            songs.add(new Song(Platform.KUGOU, hash, "",
                    Json.num(s, "AlbumID", 0),
                    Json.num(s, "MixSongID", Json.num(s, "Audioid", 0)),
                    stripTags(Json.str(s, "SongName", Json.str(s, "OriSongName", "未知歌曲"))),
                    stripTags(Json.str(s, "SingerName", "")),
                    stripTags(Json.str(s, "AlbumName", "")),
                    Json.num(s, "Duration", 0) * 1000L,
                    privilege == 10 || payType == 3,
                    // e.g. http://imge.kugou.com/stdmusic/{size}/20200909/xxx.jpg
                    Json.str(s, "Image", "").replace("{size}", "150")));
        }
        return songs;
    }

    /** song_url.js: a playable CDN URL, or an exception when the track needs VIP / has no copyright. */
    public String songUrl(Song song, String quality) throws KugouException {
        JsonObject body = KugouRequest.get("/v5/url")
                .router("trackercdn.kugou.com")
                .encryptKey()
                .dfidIfMissing(KugouCrypto.randomString(24))
                .param("album_id", song.albumId())
                .param("area_code", 1)
                .param("hash", song.id().toLowerCase())
                .param("ssa_flag", "is_fromtrack")
                .param("version", 11430)
                .param("page_id", 151369488)
                .param("quality", quality)
                .param("album_audio_id", song.trackId())
                .param("behavior", "play")
                .param("pid", 2)
                .param("cmd", 26)
                .param("pidversion", 3001)
                .param("IsFreePart", 0)
                .param("ppage_id", "463467626,350369493,788954147")
                .param("cdnBackup", 1)
                .param("module", "")
                .param("clientver", 11430)
                .json(account);
        for (String key : new String[]{"url", "backupUrl"}) {
            JsonArray urls = Json.arr(body, key);
            for (JsonElement u : urls) {
                if (u.isJsonPrimitive() && !u.getAsString().isEmpty()) return u.getAsString();
            }
        }
        throw new KugouException("拿不到播放链接（可能需要 VIP 或没有版权）");
    }

    /**
     * search_lyric.js + lyric.js. fmt=krc is preferred because only KRC carries the translation;
     * plain fmt=lrc is the fallback when the KRC cannot be decoded.
     */
    public LyricText lyric(Song song) throws KugouException {
        JsonObject search = KugouRequest.get("/v1/search")
                .base("https://lyrics.kugou.com")
                .clearDefaultParams()
                .param("album_audio_id", song.trackId())
                .param("appid", KugouRequest.APPID)
                .param("clientver", KugouRequest.CLIENTVER)
                .param("duration", song.durationMs())
                .param("hash", song.id())
                .param("keyword", song.title())
                .param("lrctxt", 1)
                .param("man", "no")
                .json(account);
        JsonArray candidates = Json.arr(search, "candidates");
        if (candidates.isEmpty() || !candidates.get(0).isJsonObject()) return LyricText.EMPTY;
        JsonObject best = candidates.get(0).getAsJsonObject();

        String krc = downloadLyric(best, "krc");
        if (!krc.isEmpty()) {
            try {
                return Krc.toLrc(KugouCrypto.decodeKrc(krc));
            } catch (Exception e) {
                KgMusic.LOGGER.warn("KuGou KRC decode failed, falling back to LRC: {}", e.toString());
            }
        }
        String lrc = downloadLyric(best, "lrc");
        // fmt=lrc content is plain base64 text
        return new LyricText(lrc.isEmpty() ? "" : new String(Base64.getDecoder().decode(lrc), StandardCharsets.UTF_8), "");
    }

    /** lyric.js: the base64 content of one lyric candidate, or "". */
    private String downloadLyric(JsonObject candidate, String fmt) throws KugouException {
        JsonObject body = KugouRequest.get("/download")
                .base("https://lyrics.kugou.com")
                .param("ver", 1)
                .param("client", "android")
                .param("id", Json.str(candidate, "id", ""))
                .param("accesskey", Json.str(candidate, "accesskey", ""))
                .param("fmt", fmt)
                .param("charset", "utf8")
                .json(account);
        return Json.str(body, "content", "");
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]*>", "").trim();
    }
}
