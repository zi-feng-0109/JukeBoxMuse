package top.nalanquede.jukeboxmuse.kugou;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import top.nalanquede.jukeboxmuse.music.MusicProvider.LyricText;

class KrcTest {
    @Test
    void convertsWordTimedLinesAndTranslation() {
        // Shape of a real KRC: metadata, a [language:] tag (romaji type 0 + translation type 1), word-timed lines
        String language = "{\"content\":[{\"language\":0,\"lyricContent\":[[\"ro\"],[\"ma\"]],\"type\":0},"
                + "{\"language\":0,\"lyricContent\":[[\"\"],[\"我们都很年轻\"]],\"type\":1}],\"version\":1}";
        String krc = "[ti:Love Story]\n[language:" + Base64.getEncoder().encodeToString(language.getBytes(StandardCharsets.UTF_8)) + "]\n"
                + "[1909,852]<0,216,0>Lyrics <216,200,0>by\n"
                + "[16321,3648]<0,271,0>We <271,289,0>were <560,448,0>young\n";
        LyricText out = Krc.toLrc(krc);
        assertEquals("[00:01.909]Lyrics by\n[00:16.321]We were young\n", out.lrc());
        assertEquals("[00:16.321]我们都很年轻\n", out.translation());
    }

    @Test
    void restoresSpacesOnlyWhenTheLineHasNone() {
        assertEquals("See you make your way", Krc.joinWords("<0,312,0>See<312,497,0>you<809,436,0>make<1245,328,0>your<1573,472,0>way"));
        // Official KRC: spaces already there, and syllables of one word must stay joined
        assertEquals("I close the flashback", Krc.joinWords("<0,232,0>I <232,288,0>close <1897,303,0>the <2200,692,0>flash<2600,100,0>back"));
        assertEquals("故事的小黄花", Krc.joinWords("<0,200,0>故事<200,200,0>的<400,200,0>小黄花"));
    }
}
