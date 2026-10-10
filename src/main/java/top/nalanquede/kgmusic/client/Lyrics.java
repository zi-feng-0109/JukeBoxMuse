package top.nalanquede.kgmusic.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed LRC: timed lines sorted by time, each with its translation ("" when there is none). */
public final class Lyrics {
    public record Line(long timeMs, String text, String translation) {
        public boolean translated() {
            return !translation.isEmpty();
        }
    }

    private static final Pattern TAG = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");
    // Translations are timed like the original; allow for rounding differences between the two files
    private static final long MATCH_TOLERANCE_MS = 150;
    public static final Lyrics EMPTY = new Lyrics(List.of());

    private final List<Line> lines;

    private Lyrics(List<Line> lines) {
        this.lines = lines;
    }

    public static Lyrics parse(String lrc) {
        return parse(lrc, "");
    }

    /** translationLrc may be "" or null; lines without a matching translation get "". */
    public static Lyrics parse(String lrc, String translationLrc) {
        List<Line> original = timed(lrc);
        if (original.isEmpty()) return EMPTY;
        List<Line> translations = timed(translationLrc);
        List<Line> lines = new ArrayList<>(original.size());
        int j = 0;
        for (Line line : original) {
            // Both lists are sorted: move to the translation closest in time to this line
            while (j + 1 < translations.size()
                    && Math.abs(translations.get(j + 1).timeMs() - line.timeMs()) <= Math.abs(translations.get(j).timeMs() - line.timeMs())) {
                j++;
            }
            String t = "";
            if (j < translations.size() && Math.abs(translations.get(j).timeMs() - line.timeMs()) <= MATCH_TOLERANCE_MS) {
                t = translations.get(j).text();
                // QQ fills untranslated lines with "//"; some files just repeat the original
                if (t.equals("//") || t.equalsIgnoreCase(line.text())) t = "";
            }
            lines.add(new Line(line.timeMs(), line.text(), t));
        }
        return new Lyrics(List.copyOf(lines));
    }

    private static List<Line> timed(String lrc) {
        if (lrc == null || lrc.isBlank()) return List.of();
        List<Line> lines = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = TAG.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            // A line can carry several time tags: [00:10.00][01:20.00]text
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                String frac = m.group(3);
                long ms = frac == null ? 0 : Long.parseLong((frac + "00").substring(0, 3));
                times.add(min * 60_000 + sec * 1000 + ms);
                end = m.end();
            }
            String text = raw.substring(end).trim();
            if (text.isEmpty()) continue;
            for (long t : times) lines.add(new Line(t, text, ""));
        }
        lines.sort(Comparator.comparingLong(Line::timeMs));
        return lines;
    }

    public List<Line> lines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Index of the line showing at positionMs, or -1 before the first line. */
    public int indexAt(long positionMs) {
        int lo = 0;
        int hi = lines.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs() <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }
}
