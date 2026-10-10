package top.nalanquede.kgmusic.netease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NeteaseCryptoTest {
    @Test
    void eapiMatchesReferenceImplementation() {
        // Computed with util/crypto.js eapi() logic (AES-128-ECB, key e82ckenh8dichen8)
        assertEquals("9B1595479B79266065903BA79E9883029338432CD7D98D1A3626E997A2C5312116D0EDC1F36E8713419EF9CC0911F9A1"
                        + "6A8A81ADD3BC66417D768CA5E02290F2CDBC54691C55C132F700B7B17156D6BA2D4575B5066B7C6D4B26B74DD1D0FB2F"
                        + "823C0D0C4580427AADFCC28BF0FA0113",
                NeteaseCrypto.eapi("/api/song/lyric", "{\"id\":1,\"header\":{\"os\":\"pc\"}}"));
    }

    @Test
    void deviceIdIsUppercaseHex() {
        assertTrue(NeteaseCrypto.deviceId().matches("[0-9A-F]{52}"));
    }
}
