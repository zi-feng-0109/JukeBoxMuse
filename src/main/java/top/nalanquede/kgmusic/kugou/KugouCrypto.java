package top.nalanquede.kgmusic.kugou;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.Inflater;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Crypto primitives used by the KuGou client protocol, ported from KuGouMusicApi util/crypto.js and util/util.js.
 */
public final class KugouCrypto {
    private static final String PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB";
    private static final String RANDOM_ALPHABET = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int[] KRC_KEY = {64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105};
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static RSAPublicKey publicKey;

    private KugouCrypto() {
    }

    public static String md5(String text) {
        return md5(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String md5(byte[] data) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("MD5").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Uppercase alphanumeric random string, same alphabet as util.randomString. */
    public static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM_ALPHABET.charAt(RANDOM.nextInt(RANDOM_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** util.getGuid: eight random 4-hex-digit groups in UUID layout. */
    public static String guid() {
        StringBuilder sb = new StringBuilder();
        int[] groups = {2, 1, 1, 1, 3};
        for (int g = 0; g < groups.length; g++) {
            if (g > 0) sb.append('-');
            for (int i = 0; i < groups[g]; i++) {
                sb.append(String.format("%04x", RANDOM.nextInt(0x10000)));
            }
        }
        return sb.toString();
    }

    /** util.calculateMid: md5 hex read as a base-16 integer, printed in decimal. */
    public static String calculateMid(String guid) {
        return new BigInteger(md5(guid), 16).toString();
    }

    public static byte[] aesCbc(int mode, byte[] data, byte[] key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("AES failed", e);
        }
    }

    /** cryptoAesEncrypt with an explicit key and iv: UTF-8 key/iv bytes, hex output. */
    public static String aesEncryptHex(String plain, String key, String iv) {
        return HEX.formatHex(aesCbc(Cipher.ENCRYPT_MODE, plain.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8), iv.getBytes(StandardCharsets.UTF_8)));
    }

    /** Result of cryptoAesEncrypt without a key: the hex ciphertext plus the random temp key used to derive key/iv. */
    public record TempKeyCipher(String hex, String tempKey) {
    }

    public static TempKeyCipher aesEncryptTempKey(String plain) {
        String tempKey = randomString(16).toLowerCase();
        String key = md5(tempKey);
        String iv = key.substring(16);
        return new TempKeyCipher(aesEncryptHex(plain, key, iv), tempKey);
    }

    /** cryptoAesDecrypt(data, tempKey): key = md5(tempKey), iv = last 16 chars of key, hex input. */
    public static String aesDecryptTempKey(String hex, String tempKey) {
        String key = md5(tempKey);
        String iv = key.substring(16);
        byte[] plain = aesCbc(Cipher.DECRYPT_MODE, HEX.parseHex(hex),
                key.getBytes(StandardCharsets.UTF_8), iv.getBytes(StandardCharsets.UTF_8));
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** playlistAesEncrypt/Decrypt key derivation: md5(key) split into 16-char key and iv. */
    public static byte[][] playlistKeyIv(String key) {
        String digest = md5(key);
        return new byte[][]{digest.substring(0, 16).getBytes(StandardCharsets.UTF_8), digest.substring(16, 32).getBytes(StandardCharsets.UTF_8)};
    }

    public static String playlistEncryptBase64(String plain, String key) {
        byte[][] kv = playlistKeyIv(key);
        return Base64.getEncoder().encodeToString(aesCbc(Cipher.ENCRYPT_MODE, plain.getBytes(StandardCharsets.UTF_8), kv[0], kv[1]));
    }

    public static String playlistDecrypt(byte[] cipherBytes, String key) {
        byte[][] kv = playlistKeyIv(key);
        return new String(aesCbc(Cipher.DECRYPT_MODE, cipherBytes, kv[0], kv[1]), StandardCharsets.UTF_8);
    }

    private static synchronized RSAPublicKey publicKey() {
        if (publicKey == null) {
            try {
                publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return publicKey;
    }

    /** rsaEncrypt2: RSA PKCS#1 v1.5, lowercase hex. */
    public static String rsaPkcs1Hex(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey());
            return HEX.formatHex(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("RSA failed", e);
        }
    }

    /** cryptoRSAEncrypt: textbook RSA on the data right-padded with zeros to the key length, hex left-padded. */
    public static String rsaRawHex(String plain) {
        RSAPublicKey key = publicKey();
        int keyLength = (key.getModulus().bitLength() + 7) / 8;
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        if (data.length > keyLength) throw new IllegalArgumentException("Data length exceeds key size");
        byte[] padded = new byte[keyLength];
        System.arraycopy(data, 0, padded, 0, data.length);
        String hex = new BigInteger(1, padded).modPow(key.getPublicExponent(), key.getModulus()).toString(16);
        return "0".repeat(Math.max(0, keyLength * 2 - hex.length())) + hex;
    }

    /** util.decodeLyrics: base64 → skip 4-byte header → XOR with KRC key → zlib inflate. */
    public static String decodeKrc(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        byte[] krc = new byte[bytes.length - 4];
        for (int i = 0; i < krc.length; i++) {
            krc[i] = (byte) (bytes[i + 4] ^ KRC_KEY[i % KRC_KEY.length]);
        }
        Inflater inflater = new Inflater();
        inflater.setInput(krc);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0 && inflater.needsInput()) break;
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            throw new IllegalStateException("KRC inflate failed", e);
        } finally {
            inflater.end();
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
