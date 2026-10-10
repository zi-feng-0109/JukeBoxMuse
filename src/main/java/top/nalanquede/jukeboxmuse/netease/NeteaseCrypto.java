package top.nalanquede.jukeboxmuse.netease;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** The eapi request encryption of the NetEase desktop client, ported from NeteaseCloudMusicApi util/crypto.js. */
final class NeteaseCrypto {
    private static final byte[] EAPI_KEY = "e82ckenh8dichen8".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private NeteaseCrypto() {
    }

    /** eapi(): the hex "params" form field for a call to url (e.g. /api/song/lyric) with JSON body text. */
    static String eapi(String url, String text) {
        String digest = md5Hex("nobody" + url + "use" + text + "md5forencrypt");
        String data = url + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(EAPI_KEY, "AES"));
            return HexFormat.of().withUpperCase().formatHex(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String md5Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 52 random uppercase hex chars, the shape of the desktop client's deviceId. */
    static String deviceId() {
        byte[] bytes = new byte[26];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
