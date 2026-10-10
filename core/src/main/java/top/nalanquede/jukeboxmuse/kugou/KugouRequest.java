package top.nalanquede.jukeboxmuse.kugou;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import top.nalanquede.jukeboxmuse.music.Json;
import top.nalanquede.jukeboxmuse.music.MusicLog;

/**
 * One KuGou API call, mirroring KuGouMusicApi util/request.js createRequest: default device params,
 * protocol headers, optional signKey and the android/web signature.
 */
public final class KugouRequest {
    public enum Sign {ANDROID, WEB}

    static final int APPID = 1005;
    static final int CLIENTVER = 20489;
    static final int SRCAPPID = 2919;
    private static final String ANDROID_SALT = "OIlwieks28dk2k092lksi2UIkp";
    private static final String WEB_SALT = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
    private static final String SIGN_KEY_SALT = "57ae12eb6890223e355ccfcb74edf70d";
    private static final String USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private String baseUrl = "https://gateway.kugou.com";
    private final String path;
    private boolean post;
    private final Map<String, Object> params = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String body = "";
    private String contentType = "application/json";
    private Sign sign = Sign.ANDROID;
    private boolean clearDefaultParams;
    private boolean encryptKey;
    private String dfidOverride;

    private KugouRequest(String path) {
        this.path = path;
    }

    public static KugouRequest get(String path) {
        return new KugouRequest(path);
    }

    public static KugouRequest post(String path) {
        KugouRequest r = new KugouRequest(path);
        r.post = true;
        return r;
    }

    public KugouRequest base(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public KugouRequest param(String key, Object value) {
        params.put(key, value);
        return this;
    }

    public KugouRequest router(String router) {
        headers.put("x-router", router);
        return this;
    }

    /** JSON body (axios sends objects as application/json). */
    public KugouRequest body(String body) {
        this.body = body;
        return this;
    }

    /** Raw string body (axios sends plain strings as a form body). */
    public KugouRequest formBody(String body) {
        this.body = body;
        this.contentType = "application/x-www-form-urlencoded";
        return this;
    }

    public KugouRequest sign(Sign sign) {
        this.sign = sign;
        return this;
    }

    public KugouRequest clearDefaultParams() {
        this.clearDefaultParams = true;
        return this;
    }

    public KugouRequest encryptKey() {
        this.encryptKey = true;
        return this;
    }

    /** song_url falls back to a random dfid when the device was never registered. */
    public KugouRequest dfidIfMissing(String dfid) {
        this.dfidOverride = dfid;
        return this;
    }

    public JsonObject json(KugouAccount account) throws KugouException {
        String text = new String(send(account), StandardCharsets.UTF_8);
        MusicLog.response("kugou " + path, text);
        JsonElement element;
        try {
            element = JsonParser.parseString(text);
        } catch (Exception e) {
            throw new KugouException("响应不是 JSON: " + abbreviate(text));
        }
        if (!element.isJsonObject()) throw new KugouException("响应格式异常: " + abbreviate(text));
        JsonObject obj = element.getAsJsonObject();
        if (obj.has("status") && obj.get("status").isJsonPrimitive() && obj.get("status").getAsInt() == 0
                || obj.has("error_code") && obj.get("error_code").isJsonPrimitive() && obj.get("error_code").getAsInt() != 0) {
            int code = obj.has("error_code") && obj.get("error_code").isJsonPrimitive() ? obj.get("error_code").getAsInt() : 0;
            String msg = Json.str(obj, "error_msg", Json.str(obj, "err_msg", abbreviate(text)));
            if (ssa) throw new KugouException("酷狗触发了安全验证（风控），请稍后再试", code, true);
            throw new KugouException("酷狗返回错误 " + code + ": " + msg, code, false);
        }
        return obj;
    }

    private boolean ssa;

    public byte[] send(KugouAccount account) throws KugouException {
        String dfid = account.hasDfid() ? account.dfid : dfidOverride != null ? dfidOverride : "-";
        long clienttime = System.currentTimeMillis() / 1000;

        Map<String, Object> all = new LinkedHashMap<>();
        if (!clearDefaultParams) {
            all.put("dfid", dfid);
            all.put("mid", account.mid);
            all.put("uuid", "-");
            all.put("appid", APPID);
            all.put("clientver", CLIENTVER);
            all.put("clienttime", clienttime);
            if (account.token != null && !account.token.isEmpty()) all.put("token", account.token);
            if (account.userid != 0) all.put("userid", account.userid);
        }
        all.putAll(params);

        if (encryptKey) {
            Object userid = all.get("userid");
            all.put("key", KugouCrypto.md5(all.get("hash") + SIGN_KEY_SALT + all.get("appid") + all.get("mid") + (userid == null ? 0 : userid)));
        }
        all.put("signature", sign == Sign.WEB ? signWeb(all, body) : signAndroid(all, body));

        String query = all.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path + "?" + query))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT);
        headers.forEach(builder::header);
        builder.header("dfid", dfid)
                .header("clienttime", String.valueOf(all.getOrDefault("clienttime", clienttime)))
                .header("mid", account.mid)
                .header("kg-rc", "1")
                .header("kg-thash", "5d816a0")
                .header("kg-rec", "1")
                .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F");
        if (post) {
            builder.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }

        HttpResponse<byte[]> response;
        try {
            response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new KugouException("网络请求失败: " + e.getMessage());
        }
        // Like request.js, an ssa-code header only matters when the call itself failed
        ssa = response.headers().firstValue("ssa-code").isPresent();
        if (response.statusCode() >= 400) {
            throw new KugouException("HTTP " + response.statusCode() + ": " + abbreviate(new String(response.body(), StandardCharsets.UTF_8)));
        }
        return response.body();
    }

    static String signAndroid(Map<String, Object> params, String body) {
        String joined = params.keySet().stream().sorted()
                .map(k -> k + "=" + params.get(k))
                .collect(Collectors.joining());
        return KugouCrypto.md5(ANDROID_SALT + joined + body + ANDROID_SALT);
    }

    static String signWeb(Map<String, Object> params, String body) {
        List<String> pairs = new ArrayList<>();
        params.forEach((k, v) -> pairs.add(k + "=" + v));
        pairs.sort(null);
        return KugouCrypto.md5(WEB_SALT + String.join("", pairs) + body + WEB_SALT);
    }

    private static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
