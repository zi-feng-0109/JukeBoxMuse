package top.nalanquede.jukeboxmuse.kugou;

import top.nalanquede.jukeboxmuse.music.MusicException;

public class KugouException extends MusicException {
    private final int errorCode;

    public KugouException(String message) {
        this(message, 0, false);
    }

    /** verificationRequired: KuGou answered with an ssa-code header, i.e. risk control wants a captcha. */
    public KugouException(String message, int errorCode, boolean verificationRequired) {
        super(message, verificationRequired);
        this.errorCode = errorCode;
    }

    public int errorCode() {
        return errorCode;
    }
}
