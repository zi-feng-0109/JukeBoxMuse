package top.nalanquede.jukeboxmuse.music;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import top.nalanquede.jukeboxmuse.JukeboxMuse;

/**
 * Minimal HTTP for the NetEase and QQ clients. Redirects are never followed: the QQ login reads its
 * cookies and the authorization code from the 302 responses themselves.
 */
public final class Http {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String url;
    private final Map<String, Object> query = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private String body;
    private String contentType;
    private Duration timeout = Duration.ofSeconds(15);

    private Http(String url) {
        this.url = url;
    }

    public static Http request(String url) {
        return new Http(url);
    }

    public Http query(String key, Object value) {
        query.put(key, value);
        return this;
    }

    public Http header(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public Http cookie(String key, String value) {
        cookies.put(key, value);
        return this;
    }

    public Http cookies(Map<String, String> all) {
        cookies.putAll(all);
        return this;
    }

    /** POST an application/x-www-form-urlencoded body. */
    public Http form(Map<String, ?> fields) {
        this.body = encode(fields);
        this.contentType = "application/x-www-form-urlencoded";
        return this;
    }

    /** POST a JSON body. */
    public Http json(JsonObject json) {
        this.body = json.toString();
        this.contentType = "application/json";
        return this;
    }

    public Http timeout(int seconds) {
        this.timeout = Duration.ofSeconds(seconds);
        return this;
    }

    public Response send() throws MusicException {
        String full = query.isEmpty() ? url : url + (url.contains("?") ? "&" : "?") + encode(query);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(full)).timeout(timeout);
        headers.forEach(builder::header);
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
        }
        if (body != null) {
            builder.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }
        HttpResponse<byte[]> response;
        try {
            response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new TimeoutException();
        } catch (Exception e) {
            throw new MusicException("网络请求失败: " + e.getMessage());
        }
        Response r = new Response(response.statusCode(), response.headers().map(), response.body());
        if (r.status >= 400) throw new MusicException("HTTP " + r.status + ": " + abbreviate(r.text()));
        return r;
    }

    public static String encode(Map<String, ?> fields) {
        return fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /** The request ran into its timeout; long-polling callers treat this as "nothing changed". */
    public static final class TimeoutException extends MusicException {
        TimeoutException() {
            super("网络请求超时");
        }
    }

    public record Response(int status, Map<String, List<String>> headers, byte[] body) {
        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public String header(String name) {
            for (var e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) return e.getValue().get(0);
            }
            return "";
        }

        /** Cookies from every Set-Cookie header; a later non-empty value wins over an earlier one. */
        public Map<String, String> cookies() {
            Map<String, String> out = new LinkedHashMap<>();
            for (var e : headers.entrySet()) {
                if (!e.getKey().equalsIgnoreCase("set-cookie")) continue;
                for (String line : e.getValue()) {
                    String pair = line.split(";", 2)[0];
                    int eq = pair.indexOf('=');
                    if (eq <= 0) continue;
                    String value = pair.substring(eq + 1).trim();
                    if (!value.isEmpty() || !out.containsKey(pair.substring(0, eq).trim())) out.put(pair.substring(0, eq).trim(), value);
                }
            }
            return out;
        }

        /** The body as a JSON object; tag names the platform in logs. */
        public JsonObject json(String tag) throws MusicException {
            String text = text();
            if (JukeboxMuse.debugLogging()) JukeboxMuse.LOGGER.info("[{}] -> {}", tag, text.length() > 4000 ? text.substring(0, 4000) + "..." : text);
            JsonElement element;
            try {
                element = JsonParser.parseString(text);
            } catch (Exception e) {
                throw new MusicException("响应不是 JSON: " + abbreviate(text));
            }
            if (!element.isJsonObject()) throw new MusicException("响应格式异常: " + abbreviate(text));
            return element.getAsJsonObject();
        }
    }
}
