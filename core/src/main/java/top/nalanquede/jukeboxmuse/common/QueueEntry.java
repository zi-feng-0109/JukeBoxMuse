package top.nalanquede.jukeboxmuse.common;

/** A queued song and the name of the player who requested it. */
public record QueueEntry(Song song, String requester) {
}
