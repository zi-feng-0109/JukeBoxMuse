package top.nalanquede.kgmusic.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LyricsTest {
    @Test
    void parsesTimedLinesAndSkipsMetadata() {
        Lyrics l = Lyrics.parse("[ti:晴天]\n[00:01.50]第一句\n[00:10.00][01:00.00]副歌\n[00:05]第二句\n");
        assertEquals(4, l.lines().size());
        assertEquals(1500, l.lines().get(0).timeMs());
        assertEquals("第二句", l.lines().get(1).text());
        assertEquals(60_000, l.lines().get(3).timeMs());
    }

    @Test
    void indexAtFindsCurrentLine() {
        Lyrics l = Lyrics.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c");
        assertEquals(-1, l.indexAt(500));
        assertEquals(0, l.indexAt(1000));
        assertEquals(1, l.indexAt(8999));
        assertEquals(2, l.indexAt(100_000));
    }

    @Test
    void pairsTranslationsByTime() {
        // QQ style: "//" placeholders, and a 10 ms rounding difference between the two files
        Lyrics l = Lyrics.parse("[00:01.00]Hello\n[00:05.00]World\n[00:09.00]Again",
                "[00:01.00]//\n[00:05.01]世界\n[00:20.00]不匹配");
        assertEquals("", l.lines().get(0).translation());
        assertEquals("世界", l.lines().get(1).translation());
        assertEquals("", l.lines().get(2).translation());
    }

    @Test
    void noTranslationKeepsPlainLines() {
        Lyrics l = Lyrics.parse("[00:01.00]第一句\n[00:05.00]第二句", "");
        assertEquals(2, l.lines().size());
        assertFalse(l.lines().get(0).translated());
    }
}
