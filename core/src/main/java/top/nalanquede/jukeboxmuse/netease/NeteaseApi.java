package top.nalanquede.jukeboxmuse.netease;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import top.nalanquede.jukeboxmuse.common.LoginMethod;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.Song;
import top.nalanquede.jukeboxmuse.music.Http;
import top.nalanquede.jukeboxmuse.music.Json;
import top.nalanquede.jukeboxmuse.music.MusicException;
import top.nalanquede.jukeboxmuse.music.MusicProvider;

/**
 * NetEase Cloud Music through the desktop client's eapi, ported from NeteaseCloudMusicApi
 * (module/login_qr_*.js, cloudsearch.js, song_url_v1.js, lyric.js and util/request.js).
 */
public final class NeteaseApi implements MusicProvider {
    private static final String DOMAIN = "https://interface.music.163.com";
    private static final String APPVER = "3.1.17.204416";
    private static final String OSVER = "Microsoft-Windows-10-Professional-build-19045-64bit";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/" + APPVER;

    private final NeteaseAccount account;

    public NeteaseApi(Path file) {
        this.account = NeteaseAccount.load(file);
    }

    @Override
    public Platform platform() {
        return Platform.NETEASE;
    }

    @Override
    public boolean isLoggedIn() {
        return account.isLoggedIn();
    }

    @Override
    public String nickname() {
        return account.nickname;
    }

    @Override
    public boolean vip() {
        return account.vipType > 0;
    }

    // ---------- login ----------

    /** login_qr_key.js + login_qr_create.js. */
    @Override
    public LoginQr loginQr(LoginMethod method) throws MusicException {
        JsonObject data = new JsonObject();
        data.addProperty("type", 3);
        JsonObject body = check(eapi("login/qrcode/unikey", data).json("netease"));
        String key = Json.str(body, "unikey", "");
        if (key.isEmpty()) throw new MusicException("获取登录二维码失败: " + body);
        return new LoginQr(method, key, "https://music.163.com/login?codekey=" + key);
    }

    /** login_qr_check.js: 800 expired, 801 waiting, 802 scanned, 803 success (the login cookies come as Set-Cookie). */
    @Override
    public LoginStatus loginCheck(LoginQr qr) throws MusicException {
        JsonObject data = new JsonObject();
        data.addProperty("key", qr.key());
        data.addProperty("type", 3);
        Http.Response response = eapi("login/qrcode/client/login", data);
        JsonObject body = response.json("netease");
        int code = (int) Json.num(body, "code", 0);
        switch (code) {
            case 800:
                return LoginStatus.EXPIRED;
            case 801:
                return LoginStatus.WAITING;
            case 802:
                return LoginStatus.SCANNED;
            case 803:
                break;
            default:
                check(body);
                return LoginStatus.WAITING;
        }
        Map<String, String> cookies = response.cookies();
        String musicU = cookies.getOrDefault("MUSIC_U", "");
        if (musicU.isEmpty()) throw new MusicException("网易云登录成功但没有返回 MUSIC_U");
        synchronized (account) {
            account.musicU = musicU;
            account.csrf = cookies.getOrDefault("__csrf", "");
            account.lastRefreshMs = System.currentTimeMillis();
            account.save();
        }
        try {
            refresh();
        } catch (MusicException ignored) {
            // The nickname is cosmetic; the login itself worked
        }
        return LoginStatus.SUCCESS;
    }

    /** user_account.js: re-reads nickname and VIP state and checks the cookie still works. */
    @Override
    public void refresh() throws MusicException {
        JsonObject body = check(eapi("nuser/account/get", new JsonObject()).json("netease"));
        JsonObject acc = Json.obj(body, "account");
        JsonObject profile = Json.obj(body, "profile");
        if (acc == null) throw new MusicException("网易云登录已失效，请重新扫码登录");
        synchronized (account) {
            account.userId = Json.num(acc, "id", account.userId);
            account.vipType = (int) Json.num(acc, "vipType", 0);
            account.nickname = Json.str(profile, "nickname", account.nickname);
            account.lastRefreshMs = System.currentTimeMillis();
            account.save();
        }
    }

    @Override
    public long lastRefreshMs() {
        return account.lastRefreshMs;
    }

    @Override
    public void logout() {
        account.logout();
    }

    // ---------- music ----------

    /** cloudsearch.js (type=1, songs). */
    @Override
    public List<Song> search(String keyword, int page, int pageSize) throws MusicException {
        JsonObject data = new JsonObject();
        data.addProperty("s", keyword);
        data.addProperty("type", 1);
        data.addProperty("limit", pageSize);
        data.addProperty("offset", (page - 1) * pageSize);
        data.addProperty("total", true);
        JsonObject body = check(eapi("cloudsearch/pc", data).json("netease"));
        List<Song> songs = new ArrayList<>();
        for (JsonElement e : Json.arr(Json.obj(body, "result"), "songs")) {
            if (!e.isJsonObject()) continue;
            JsonObject s = e.getAsJsonObject();
            long id = Json.num(s, "id", 0);
            if (id == 0) continue;
            List<String> artists = new ArrayList<>();
            for (JsonElement a : Json.arr(s, "ar")) {
                if (a.isJsonObject()) artists.add(Json.str(a.getAsJsonObject(), "name", ""));
            }
            JsonObject album = Json.obj(s, "al");
            long fee = Json.num(s, "fee", 0);
            songs.add(new Song(Platform.NETEASE, String.valueOf(id), "", Json.num(album, "id", 0), id,
                    Json.str(s, "name", "未知歌曲"),
                    String.join("/", artists),
                    Json.str(album, "name", ""),
                    Json.num(s, "dt", 0),
                    // 1: VIP only, 4: paid album
                    fee == 1 || fee == 4,
                    cover(Json.str(album, "picUrl", ""))));
        }
        return songs;
    }

    /** The CDN scales covers on request; 150 px is plenty for the music screen. */
    private static String cover(String picUrl) {
        return picUrl.isEmpty() ? "" : picUrl + "?param=150y150";
    }

    /** song_url_v1.js. A non-VIP account only gets a 30 s preview of VIP songs (freeTrialInfo), which is not worth playing. */
    @Override
    public String songUrl(Song song, String quality) throws MusicException {
        JsonObject data = new JsonObject();
        data.addProperty("ids", "[" + song.id() + "]");
        data.addProperty("level", "320".equals(quality) ? "exhigh" : "standard");
        data.addProperty("encodeType", "mp3");
        JsonObject body = check(eapi("song/enhance/player/url/v1", data).json("netease"));
        JsonArray list = Json.arr(body, "data");
        JsonObject item = !list.isEmpty() && list.get(0).isJsonObject() ? list.get(0).getAsJsonObject() : null;
        String url = Json.str(item, "url", "");
        if (url.isEmpty()) throw new MusicException("拿不到播放链接（可能需要 VIP 或没有版权）");
        if (Json.obj(item, "freeTrialInfo") != null) throw new MusicException("只能试听片段（需要黑胶 VIP）");
        String type = Json.str(item, "type", "mp3");
        if (!type.equalsIgnoreCase("mp3")) throw new MusicException("音频格式 " + type + " 无法播放");
        return url;
    }

    /** lyric.js; tlyric is the translation. */
    @Override
    public LyricText lyric(Song song) throws MusicException {
        JsonObject data = new JsonObject();
        data.addProperty("id", song.id());
        for (String k : new String[]{"tv", "lv", "rv", "kv"}) data.addProperty(k, -1);
        data.addProperty("_nmclfl", 1);
        JsonObject body = check(eapi("song/lyric", data).json("netease"));
        return new LyricText(Json.str(Json.obj(body, "lrc"), "lyric", ""), Json.str(Json.obj(body, "tlyric"), "lyric", ""));
    }

    // ---------- request ----------

    /** util/request.js with crypto=eapi: the device "header" goes both into the cookie and into the encrypted body. */
    private Http.Response eapi(String path, JsonObject data) throws MusicException {
        Map<String, String> header = new LinkedHashMap<>();
        header.put("osver", OSVER);
        header.put("deviceId", account.deviceId);
        header.put("os", "pc");
        header.put("appver", APPVER);
        header.put("versioncode", "140");
        header.put("mobilename", "");
        header.put("buildver", String.valueOf(System.currentTimeMillis() / 1000));
        header.put("resolution", "1920x1080");
        header.put("__csrf", account.csrf == null ? "" : account.csrf);
        header.put("channel", "netease");
        header.put("requestId", System.currentTimeMillis() + "_" + String.format("%04d", ThreadLocalRandom.current().nextInt(1000)));
        if (account.isLoggedIn()) header.put("MUSIC_U", account.musicU);

        JsonObject h = new JsonObject();
        header.forEach(h::addProperty);
        data.add("header", h);
        String url = "/api/" + path;

        Http request = Http.request(DOMAIN + "/eapi/" + path)
                .header("User-Agent", USER_AGENT)
                .form(Map.of("params", NeteaseCrypto.eapi(url, data.toString())));
        header.forEach((k, v) -> request.cookie(urlEncode(k), urlEncode(v)));
        return request.send();
    }

    /** Throws unless the body has code 200. */
    private static JsonObject check(JsonObject body) throws MusicException {
        int code = (int) Json.num(body, "code", 0);
        if (code == 200) return body;
        String msg = Json.str(body, "message", Json.str(body, "msg", Http.abbreviate(body.toString())));
        // -460 / -462 / 8821: "网络太拥挤" / slider captcha required
        if (code == -460 || code == -462 || code == 8821) throw new MusicException("网易云触发了安全验证（风控），请稍后再试", true);
        if (code == 301) throw new MusicException("网易云登录已失效，请重新扫码登录");
        throw new MusicException("网易云返回错误 " + code + ": " + msg);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
