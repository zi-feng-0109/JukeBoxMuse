package top.nalanquede.jukeboxmuse.kugou;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import top.nalanquede.jukeboxmuse.music.Json;
import top.nalanquede.jukeboxmuse.music.MusicProvider.LyricText;

/**
 * Turns decoded KRC into LRC. KRC lines look like [startMs,durationMs]&lt;offset,duration,0&gt;word...; the
 * translation sits in a base64 JSON [language:] tag, one entry per timed line, in content items of type 1.
 */
final class Krc {
    private static final Pattern LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern WORD_TAG = Pattern.compile("<\\d+,\\d+,\\d+>");
    private static final int TYPE_TRANSLATION = 1;

    private Krc() {
    }

    static LyricText toLrc(String krc) {
        List<Long> times = new ArrayList<>();
        StringBuilder lrc = new StringBuilder();
        String language = "";
        for (String raw : krc.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("[language:") && line.endsWith("]")) {
                language = line.substring(10, line.length() - 1);
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches()) continue;
            long time = Long.parseLong(m.group(1));
            times.add(time);
            lrc.append(stamp(time)).append(joinWords(m.group(3))).append('\n');
        }
        return new LyricText(lrc.toString(), translation(language, times));
    }

    /**
     * Official KRCs keep the spaces inside the word segments ("We ", "were "). Some user-uploaded ones drop
     * them ("See", "you"); when no segment of the line has a space, put one back between Latin words.
     */
    static String joinWords(String body) {
        String[] parts = WORD_TAG.split(body, -1);
        boolean spaced = false;
        for (String part : parts) {
            if (part.contains(" ")) spaced = true;
        }
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!spaced && !out.isEmpty() && isLatin(out.charAt(out.length() - 1)) && isLatin(part.charAt(0))) out.append(' ');
            out.append(part);
        }
        return out.toString().trim();
    }

    private static boolean isLatin(char c) {
        return c < 0x80 && Character.isLetterOrDigit(c) || c == '\'';
    }

    private static String translation(String language, List<Long> times) {
        if (language.isEmpty()) return "";
        JsonObject root;
        try {
            root = JsonParser.parseString(new String(Base64.getDecoder().decode(language), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return "";
        }
        for (JsonElement c : Json.arr(root, "content")) {
            if (!c.isJsonObject() || Json.num(c.getAsJsonObject(), "type", -1) != TYPE_TRANSLATION) continue;
            JsonArray lines = Json.arr(c.getAsJsonObject(), "lyricContent");
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < lines.size() && i < times.size(); i++) {
                StringBuilder text = new StringBuilder();
                if (lines.get(i).isJsonArray()) {
                    for (JsonElement part : lines.get(i).getAsJsonArray()) {
                        if (part.isJsonPrimitive()) text.append(part.getAsString());
                    }
                }
                if (!text.toString().isBlank()) out.append(stamp(times.get(i))).append(text.toString().trim()).append('\n');
            }
            return out.toString();
        }
        return "";
    }

    private static String stamp(long ms) {
        return String.format("[%02d:%02d.%03d]", ms / 60_000, ms / 1000 % 60, ms % 1000);
    }
}
