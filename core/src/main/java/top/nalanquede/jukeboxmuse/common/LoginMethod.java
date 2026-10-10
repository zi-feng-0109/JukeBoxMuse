package top.nalanquede.jukeboxmuse.common;

/** The QR code logins the account page offers. QQ Music can be logged into with either a QQ or a WeChat scan. */
public enum LoginMethod {
    KUGOU(Platform.KUGOU, "扫码登录", "请用酷狗音乐 App 扫描二维码"),
    NETEASE(Platform.NETEASE, "扫码登录", "请用网易云音乐 App 扫描二维码"),
    QQ(Platform.QQ, "QQ 扫码", "请用手机 QQ 扫描二维码"),
    WECHAT(Platform.QQ, "微信扫码", "请用微信扫描二维码（微信需绑定 QQ 音乐）");

    private final Platform platform;
    private final String buttonLabel;
    private final String scanHint;

    LoginMethod(Platform platform, String buttonLabel, String scanHint) {
        this.platform = platform;
        this.buttonLabel = buttonLabel;
        this.scanHint = scanHint;
    }

    public Platform platform() {
        return platform;
    }

    public String buttonLabel() {
        return buttonLabel;
    }

    public String scanHint() {
        return scanHint;
    }
}
