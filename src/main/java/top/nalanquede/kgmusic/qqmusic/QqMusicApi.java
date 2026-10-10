package top.nalanquede.kgmusic.qqmusic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import top.nalanquede.kgmusic.common.LoginMethod;
import top.nalanquede.kgmusic.common.Platform;
import top.nalanquede.kgmusic.common.Song;
import top.nalanquede.kgmusic.music.Http;
import top.nalanquede.kgmusic.music.Json;
import top.nalanquede.kgmusic.music.MusicException;
import top.nalanquede.kgmusic.music.MusicProvider;

/**
 * QQ Music through the desktop musicu.fcg gateway, ported from QQMusicApi (modules/login.py, search.py, song.py,
 * lyric.py). Login goes through QQ's ptlogin QR code (then OAuth into QQ Music) or WeChat's open-platform QR code.
 */
public final class QqMusicApi implements MusicProvider {
    private static final String MUSICU = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String STREAM_HOST = "https://isure.stream.qqmusic.qq.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String PT_APPID = "716027609";
    private static final String PT_DAID = "383";
    private static final String QQ_MUSIC_CLIENT_ID = "100497308";
    private static final String WX_APPID = "wx48db31d50e334801";

    private static final Pattern PTUI_CB = Pattern.compile("ptuiCB\\((.*?)\\)");
    private static final Pattern PTUI_ARG = Pattern.compile("'((?:\\\\.|[^'])*)'");
    private static final Pattern PT_SIGX = Pattern.compile("[?&]ptsigx=(.+?)&s_url");
    private static final Pattern PT_UIN = Pattern.compile("[?&]uin=(.+?)&service");
    private static final Pattern AUTH_CODE = Pattern.compile("[?&]code=([^&]+)");
    private static final Pattern WX_UUID = Pattern.compile("uuid=(.+?)\"");
    private static final Pattern WX_STATUS = Pattern.compile("window\\.wx_errcode=(\\d+);window\\.wx_code='([^']*)'");

    private final QqAccount account;

    public QqMusicApi(Path file) {
        this.account = QqAccount.load(file);
    }

    @Override
    public Platform platform() {
        return Platform.QQ;
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
        return false;
    }

    // ---------- login ----------

    @Override
    public LoginQr loginQr(LoginMethod method) throws MusicException {
        return method == LoginMethod.WECHAT ? wechatQr() : qqQr();
    }

    @Override
    public LoginStatus loginCheck(LoginQr qr) throws MusicException {
        return qr.method() == LoginMethod.WECHAT ? wechatCheck(qr.key()) : qqCheck(qr.key());
    }

    /** _get_qq_qr: the key is the qrsig cookie that comes with the PNG. */
    private LoginQr qqQr() throws MusicException {
        Http.Response r = Http.request("https://ssl.ptlogin2.qq.com/ptqrshow")
                .query("appid", PT_APPID).query("e", 2).query("l", "M").query("s", 3).query("d", 72).query("v", 4)
                .query("t", ThreadLocalRandom.current().nextDouble())
                .query("daid", PT_DAID).query("pt_3rd_aid", QQ_MUSIC_CLIENT_ID)
                .header("Referer", "https://xui.ptlogin2.qq.com/")
                .header("User-Agent", USER_AGENT)
                .send();
        String qrsig = r.cookies().getOrDefault("qrsig", "");
        if (qrsig.isEmpty()) throw new MusicException("获取 QQ 登录二维码失败");
        return new LoginQr(LoginMethod.QQ, qrsig, QrImage.decode(r.body()));
    }

    /** _check_qq_qr: ptuiCB('code', ..., 'redirect url', ...); 0 done, 66 waiting, 67 scanned, 65 expired, 68 refused. */
    private LoginStatus qqCheck(String qrsig) throws MusicException {
        String text = Http.request("https://ssl.ptlogin2.qq.com/ptqrlogin")
                .query("u1", "https://graph.qq.com/oauth2.0/login_jump")
                .query("ptqrtoken", Hash33.of(qrsig, 0))
                .query("ptredirect", 0).query("h", 1).query("t", 1).query("g", 1).query("from_ui", 1)
                .query("ptlang", 2052)
                .query("action", "0-0-" + System.currentTimeMillis())
                .query("js_ver", 20102616).query("js_type", 1).query("pt_uistyle", 40)
                .query("aid", PT_APPID).query("daid", PT_DAID).query("pt_3rd_aid", QQ_MUSIC_CLIENT_ID).query("has_onekey", 1)
                .header("Referer", "https://xui.ptlogin2.qq.com/")
                .header("User-Agent", USER_AGENT)
                .cookie("qrsig", qrsig)
                .send().text();
        Matcher cb = PTUI_CB.matcher(text);
        if (!cb.find()) throw new MusicException("QQ 二维码状态无法解析: " + Http.abbreviate(text));
        List<String> args = new ArrayList<>();
        Matcher arg = PTUI_ARG.matcher(cb.group(1));
        while (arg.find()) args.add(arg.group(1));
        String code = args.isEmpty() ? "" : args.get(0);
        switch (code) {
            case "0":
                break;
            case "65":
                return LoginStatus.EXPIRED;
            case "67":
                return LoginStatus.SCANNED;
            case "68":
                return LoginStatus.REFUSED;
            default:
                return LoginStatus.WAITING;
        }
        String redirect = args.size() > 2 ? args.get(2) : "";
        Matcher sigx = PT_SIGX.matcher(redirect);
        Matcher uin = PT_UIN.matcher(redirect);
        if (!sigx.find() || !uin.find()) throw new MusicException("QQ 登录凭据无法解析");
        authorizeQq(uin.group(1), sigx.group(1));
        return LoginStatus.SUCCESS;
    }

    /** _authorize_qq_qr: ptlogin ticket → p_skey cookie → OAuth code for QQ Music → musickey. */
    private void authorizeQq(String uin, String sigx) throws MusicException {
        Http.Response sig = Http.request("https://ssl.ptlogin2.graph.qq.com/check_sig")
                .query("uin", uin).query("pttype", 1).query("service", "ptqrlogin").query("nodirect", 0)
                .query("ptsigx", sigx).query("s_url", "https://graph.qq.com/oauth2.0/login_jump")
                .query("ptlang", 2052).query("ptredirect", 100).query("aid", PT_APPID).query("daid", PT_DAID)
                .query("j_later", 0).query("low_login_hour", 0).query("regmaster", 0).query("pt_login_type", 3)
                .query("pt_aid", 0).query("pt_aaid", 16).query("pt_light", 0).query("pt_3rd_aid", QQ_MUSIC_CLIENT_ID)
                .header("Referer", "https://xui.ptlogin2.qq.com/")
                .header("User-Agent", USER_AGENT)
                .send();
        Map<String, String> cookies = sig.cookies();
        String pSkey = cookies.getOrDefault("p_skey", "");
        if (pSkey.isEmpty()) throw new MusicException("QQ 登录失败：拿不到 p_skey");

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("response_type", "code");
        form.put("client_id", QQ_MUSIC_CLIENT_ID);
        form.put("redirect_uri", "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/");
        form.put("scope", "get_user_info,get_app_friends");
        form.put("state", "state");
        form.put("switch", "");
        form.put("from_ptlogin", 1);
        form.put("src", 1);
        form.put("update_auth", 1);
        form.put("openapi", "1010_1030");
        form.put("g_tk", Hash33.of(pSkey, 5381));
        form.put("auth_time", System.currentTimeMillis() / 1000 * 1000);
        form.put("ui", UUID.randomUUID().toString());
        String location = Http.request("https://graph.qq.com/oauth2.0/authorize")
                .header("User-Agent", USER_AGENT)
                .cookies(cookies)
                .form(form)
                .send().header("Location");
        Matcher code = AUTH_CODE.matcher(location);
        if (!code.find()) throw new MusicException("QQ 授权失败：拿不到 code");

        JsonObject param = new JsonObject();
        param.addProperty("code", code.group(1));
        login(cgi("QQConnectLogin.LoginServer", "QQLogin", param, 2));
    }

    /** _get_wx_qr: the uuid comes from the qrconnect page; the QR code encodes the confirm URL. */
    private LoginQr wechatQr() throws MusicException {
        String page = Http.request("https://open.weixin.qq.com/connect/qrconnect")
                .query("appid", WX_APPID)
                .query("redirect_uri", "https://y.qq.com/portal/wx_redirect.html?login_type=2&surl=https://y.qq.com/")
                .query("response_type", "code")
                .query("scope", "snsapi_login")
                .query("state", "STATE")
                .query("href", "https://y.qq.com/mediastyle/music_v17/src/css/popup_wechat.css#wechat_redirect")
                .header("User-Agent", USER_AGENT)
                .send().text();
        Matcher m = WX_UUID.matcher(page);
        if (!m.find()) throw new MusicException("获取微信登录二维码失败");
        String uuid = m.group(1);
        return new LoginQr(LoginMethod.WECHAT, uuid, "https://open.weixin.qq.com/connect/confirm?uuid=" + uuid);
    }

    /**
     * _check_wx_qr: a long poll that answers after ~15 s when nothing happens.
     * 405 done (with code), 408 waiting, 404 scanned, 402 expired, 403 refused.
     */
    private LoginStatus wechatCheck(String uuid) throws MusicException {
        String text;
        try {
            text = Http.request("https://lp.open.weixin.qq.com/connect/l/qrconnect")
                    .query("uuid", uuid)
                    .query("_", System.currentTimeMillis())
                    .header("Referer", "https://open.weixin.qq.com/")
                    .header("User-Agent", USER_AGENT)
                    .timeout(35)
                    .send().text();
        } catch (Http.TimeoutException e) {
            return LoginStatus.WAITING;
        }
        Matcher m = WX_STATUS.matcher(text);
        if (!m.find()) throw new MusicException("微信二维码状态无法解析: " + Http.abbreviate(text));
        switch (m.group(1)) {
            case "405":
                break;
            case "404":
                return LoginStatus.SCANNED;
            case "402":
                return LoginStatus.EXPIRED;
            case "403":
                return LoginStatus.REFUSED;
            default:
                return LoginStatus.WAITING;
        }
        JsonObject param = new JsonObject();
        param.addProperty("code", m.group(2));
        param.addProperty("strAppid", WX_APPID);
        login(cgi("music.login.LoginServer", "Login", param, 1));
        return LoginStatus.SUCCESS;
    }

    private void login(JsonObject credential) throws MusicException {
        if (Json.num(credential, "musicid", 0) == 0 || Json.str(credential, "musickey", "").isEmpty()) {
            throw new MusicException("QQ 音乐登录失败: " + Http.abbreviate(credential.toString()));
        }
        account.update(credential);
        fetchNickname();
    }

    /** refresh_credential: logs in again with the refresh key, which also extends the musickey. */
    @Override
    public void refresh() throws MusicException {
        if (!account.isLoggedIn()) throw new MusicException("未登录");
        JsonObject param = new JsonObject();
        param.addProperty("openid", account.openid);
        param.addProperty("refresh_token", account.refreshToken);
        param.addProperty("musickey", account.musickey);
        param.addProperty("refresh_key", account.refreshKey);
        param.addProperty("loginMode", 2);
        if (account.loginType == 1) {
            param.addProperty("str_musicid", account.uin());
            param.addProperty("unionid", account.unionid);
        } else {
            param.addProperty("access_token", account.accessToken);
            param.addProperty("expired_in", account.expiredAt);
            param.addProperty("musicid", account.musicid);
        }
        JsonObject data = cgi("music.login.LoginServer", "Login", param, account.loginType);
        if (!Json.str(data, "musickey", "").isEmpty()) account.update(data);
        if (account.nickname.isEmpty()) fetchNickname();
    }

    /** check_expired's web profile call; only used for the nickname shown on the account page. */
    private void fetchNickname() {
        try {
            JsonObject body = Http.request("https://c6.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg")
                    .query("format", "json").query("inCharset", "utf-8").query("outCharset", "utf-8")
                    .query("notice", 0).query("cid", 205360838).query("needNewCode", 0)
                    .query("loginUin", account.uin()).query("hostUin", 0).query("userid", account.uin()).query("reqfrom", 1)
                    .query("g_tk", gtk())
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://y.qq.com/")
                    .cookies(loginCookies())
                    .send().json("qqmusic");
            String nick = Json.str(Json.obj(Json.obj(body, "data"), "creator"), "nick", "");
            synchronized (account) {
                account.nickname = nick.isEmpty() ? account.uin() : nick;
                account.save();
            }
        } catch (MusicException e) {
            synchronized (account) {
                if (account.nickname.isEmpty()) account.nickname = account.uin();
                account.save();
            }
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

    /** search_by_type (song) through the desktop search service. */
    @Override
    public List<Song> search(String keyword, int page, int pageSize) throws MusicException {
        JsonObject param = new JsonObject();
        param.addProperty("query", keyword);
        param.addProperty("search_type", 0);
        param.addProperty("num_per_page", pageSize);
        param.addProperty("page_num", page);
        JsonObject data = cgi("music.search.SearchCgiService", "DoSearchForQQMusicDesktop", param, -1);
        List<Song> songs = new ArrayList<>();
        for (JsonElement e : Json.arr(Json.obj(Json.obj(data, "body"), "song"), "list")) {
            if (!e.isJsonObject()) continue;
            JsonObject s = e.getAsJsonObject();
            String mid = Json.str(s, "mid", "");
            if (mid.isEmpty()) continue;
            List<String> singers = new ArrayList<>();
            for (JsonElement a : Json.arr(s, "singer")) {
                if (a.isJsonObject()) singers.add(Json.str(a.getAsJsonObject(), "name", ""));
            }
            JsonObject album = Json.obj(s, "album");
            songs.add(new Song(Platform.QQ, mid, Json.str(Json.obj(s, "file"), "media_mid", ""),
                    Json.num(album, "id", 0), Json.num(s, "id", 0),
                    stripTags(Json.str(s, "name", Json.str(s, "title", "未知歌曲"))),
                    stripTags(String.join("/", singers)),
                    stripTags(Json.str(album, "name", "")),
                    Json.num(s, "interval", 0) * 1000L,
                    Json.num(Json.obj(s, "pay"), "pay_play", 0) == 1,
                    cover(Json.str(album, "mid", ""))));
        }
        return songs;
    }

    private static String cover(String albumMid) {
        return albumMid.isEmpty() ? "" : "https://y.gtimg.cn/music/photo_new/T002R150x150M000" + albumMid + ".jpg";
    }

    /** get_song_urls: M800 = 320k mp3, M500 = 128k mp3. 320 falls back to 128 when the account cannot get it. */
    @Override
    public String songUrl(Song song, String quality) throws MusicException {
        String[] prefixes = "320".equals(quality) ? new String[]{"M800", "M500"} : new String[]{"M500"};
        for (String prefix : prefixes) {
            String file = prefix + (song.fileId().isEmpty() ? song.id() + song.id() : song.fileId()) + ".mp3";
            JsonObject param = new JsonObject();
            param.addProperty("uin", account.isLoggedIn() ? account.uin() : "");
            param.add("filename", array(file));
            param.addProperty("guid", account.guid);
            param.add("songmid", array(song.id()));
            JsonArray types = new JsonArray();
            types.add(0);
            param.add("songtype", types);
            param.addProperty("ctx", 0);
            JsonObject data = cgi("music.vkey.GetVkey", "UrlGetVkey", param, -1);
            JsonArray infos = Json.arr(data, "midurlinfo");
            String purl = !infos.isEmpty() && infos.get(0).isJsonObject() ? Json.str(infos.get(0).getAsJsonObject(), "purl", "") : "";
            if (purl.isEmpty()) continue;
            JsonArray sip = Json.arr(data, "sip");
            String host = !sip.isEmpty() && sip.get(0).isJsonPrimitive() && !sip.get(0).getAsString().isEmpty() ? sip.get(0).getAsString() : STREAM_HOST;
            return host + purl;
        }
        throw new MusicException("拿不到播放链接（可能需要 VIP 或没有版权）");
    }

    /** get_lyric with crypt=0: the LRC and its translation come back base64-encoded. */
    @Override
    public LyricText lyric(Song song) throws MusicException {
        JsonObject param = new JsonObject();
        param.addProperty("songMID", song.id());
        param.addProperty("crypt", 0);
        param.addProperty("qrc", 0);
        param.addProperty("trans", 1);
        param.addProperty("roma", 0);
        JsonObject data = cgi("music.musichallSong.PlayLyricInfo", "GetPlayLyricInfo", param, -1);
        return new LyricText(base64(Json.str(data, "lyric", "")), base64(Json.str(data, "trans", "")));
    }

    private static String base64(String text) {
        try {
            return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text;
        }
    }

    // ---------- request ----------

    /**
     * One musicu.fcg call with the desktop "comm" block. loginType overrides comm.tmeLoginType for the login calls;
     * -1 uses the stored account's. Returns req_0.data.
     */
    private JsonObject cgi(String module, String method, JsonObject param, int loginType) throws MusicException {
        boolean loggedIn = account.isLoggedIn();
        JsonObject comm = new JsonObject();
        comm.addProperty("ct", 19);
        comm.addProperty("cv", 2201);
        comm.addProperty("chid", "0");
        comm.addProperty("uin", loggedIn ? account.musicid : 0);
        comm.addProperty("g_tk", gtk());
        comm.addProperty("guid", account.guid.toUpperCase());
        if (loggedIn) {
            comm.addProperty("qq", account.uin());
            comm.addProperty("authst", account.musickey);
        }
        int type = loginType >= 0 ? loginType : loggedIn ? account.loginType : 0;
        if (type > 0) comm.addProperty("tmeLoginType", type);

        JsonObject req = new JsonObject();
        req.addProperty("module", module);
        req.addProperty("method", method);
        req.add("param", param);
        JsonObject payload = new JsonObject();
        payload.add("comm", comm);
        payload.add("req_0", req);

        JsonObject body = Http.request(MUSICU)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y.qq.com/")
                .cookies(loginCookies())
                .json(payload)
                .send().json("qqmusic");
        JsonObject result = Json.obj(body, "req_0");
        long code = Json.num(result, "code", Json.num(body, "code", -1));
        if (code == 0) {
            JsonObject data = Json.obj(result, "data");
            return data != null ? data : new JsonObject();
        }
        if (code == 1000 || code == 104400 || code == 104401) throw new MusicException("QQ 音乐登录已失效，请重新扫码登录");
        if (code == 104604) throw new MusicException("QQ 音乐操作过于频繁，请稍后再试", true);
        throw new MusicException("QQ 音乐返回错误 " + code + ": " + Http.abbreviate(String.valueOf(result)));
    }

    private Map<String, String> loginCookies() {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (account.isLoggedIn()) {
            cookies.put("uin", account.uin());
            cookies.put("qqmusic_uin", account.uin());
            cookies.put("qm_keyst", account.musickey);
            cookies.put("qqmusic_key", account.musickey);
        }
        return cookies;
    }

    private long gtk() {
        return account.isLoggedIn() ? Hash33.of(account.musickey, 5381) : 5381;
    }

    private static JsonArray array(String value) {
        JsonArray a = new JsonArray();
        a.add(value);
        return a;
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]*>", "").trim();
    }
}
