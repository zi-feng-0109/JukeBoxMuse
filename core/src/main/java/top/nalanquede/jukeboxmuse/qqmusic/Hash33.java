package top.nalanquede.jukeboxmuse.qqmusic;

/** QQ's hash33 (QQMusicApi utils/common.py), used for ptqrtoken and every g_tk. */
final class Hash33 {
    private Hash33() {
    }

    /** Python computes this with unbounded ints; long overflow keeps the same low 31 bits. */
    static long of(String s, long h) {
        for (int i = 0; i < s.length(); i++) {
            h = (h << 5) + h + s.charAt(i);
        }
        return h & 0x7fffffffL;
    }
}
