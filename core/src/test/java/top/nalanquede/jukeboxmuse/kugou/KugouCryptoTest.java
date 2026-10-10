package top.nalanquede.jukeboxmuse.kugou;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

class KugouCryptoTest {
    @Test
    void md5MatchesKnownVector() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", KugouCrypto.md5("hello"));
    }

    @Test
    void midIsMd5AsDecimal() {
        String guid = "abc";
        assertEquals(new BigInteger(KugouCrypto.md5(guid), 16).toString(), KugouCrypto.calculateMid(guid));
    }

    @Test
    void guidHasUuidLayout() {
        assertTrue(KugouCrypto.guid().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void rsaOutputsAreKeyLength() {
        assertEquals(256, KugouCrypto.rsaRawHex("{\"clienttime_ms\":1,\"key\":\"abc\"}").length());
        assertEquals(256, KugouCrypto.rsaPkcs1Hex("{\"aes\":\"abcdef\",\"uid\":0,\"token\":\"\"}").length());
    }

    @Test
    void tempKeyAesRoundTrips() {
        KugouCrypto.TempKeyCipher c = KugouCrypto.aesEncryptTempKey("{\"token\":\"x\"}");
        assertEquals("{\"token\":\"x\"}", KugouCrypto.aesDecryptTempKey(c.hex(), c.tempKey()));
    }

    @Test
    void playlistAesRoundTrips() {
        String b64 = KugouCrypto.playlistEncryptBase64("{\"a\":1}", "abcdef");
        assertEquals("{\"a\":1}", KugouCrypto.playlistDecrypt(Base64.getDecoder().decode(b64), "abcdef"));
    }

    @Test
    void androidSignatureSortsKeys() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("b", 2);
        params.put("a", "x");
        String salt = "OIlwieks28dk2k092lksi2UIkp";
        assertEquals(KugouCrypto.md5(salt + "a=xb=2" + "body" + salt), KugouRequest.signAndroid(params, "body"));
    }

    @Test
    void webSignatureSortsPairs() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("plat", 4);
        params.put("appid", 1001);
        String salt = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
        assertEquals(KugouCrypto.md5(salt + "appid=1001plat=4" + salt), KugouRequest.signWeb(params, ""));
    }

    @Test
    void krcDecodeReversesEncoding() {
        String text = "[0,1000]<0,500,0>你<500,500,0>好";
        Deflater deflater = new Deflater();
        deflater.setInput(text.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) z.write(buf, 0, deflater.deflate(buf));
        byte[] zipped = z.toByteArray();
        int[] key = {64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105};
        byte[] krc = new byte[zipped.length + 4];
        System.arraycopy("krc1".getBytes(StandardCharsets.US_ASCII), 0, krc, 0, 4);
        for (int i = 0; i < zipped.length; i++) krc[i + 4] = (byte) (zipped[i] ^ key[i % 16]);
        assertEquals(text, KugouCrypto.decodeKrc(Base64.getEncoder().encodeToString(krc)));
    }
}
