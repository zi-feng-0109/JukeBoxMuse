package top.nalanquede.jukeboxmuse.common;

/**
 * A song as a platform's search returns it. id is what the platform keys the track by (KuGou hash, NetEase song id,
 * QQ songmid); fileId, albumId and trackId are the extra ids some platforms need for song_url and lyrics
 * (QQ media_mid; KuGou album_id and album_audio_id). coverUrl is a small album cover image, "" when unknown.
 */
public record Song(Platform platform, String id, String fileId, long albumId, long trackId,
                   String name, String singer, String album, long durationMs, boolean vip, String coverUrl) {
    public String title() {
        return singer.isEmpty() ? name : name + " - " + singer;
    }

    /** Same track on the same platform. */
    public boolean sameTrack(Song other) {
        return platform == other.platform && id.equalsIgnoreCase(other.id);
    }
}
