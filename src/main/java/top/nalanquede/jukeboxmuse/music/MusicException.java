package top.nalanquede.jukeboxmuse.music;

/** A failed music platform call. The message is shown to players, so it is in Chinese. */
public class MusicException extends Exception {
    private final boolean verificationRequired;

    public MusicException(String message) {
        this(message, false);
    }

    public MusicException(String message, boolean verificationRequired) {
        super(message);
        this.verificationRequired = verificationRequired;
    }

    /** The platform's risk control wants a captcha, so stop calling it for a while. */
    public boolean verificationRequired() {
        return verificationRequired;
    }
}
