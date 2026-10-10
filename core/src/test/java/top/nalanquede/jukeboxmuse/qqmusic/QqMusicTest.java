package top.nalanquede.jukeboxmuse.qqmusic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

class QqMusicTest {
    @Test
    void hash33MatchesPython() {
        // utils/common.py hash33 with Python's unbounded ints
        assertEquals(108966, Hash33.of("abc", 0));
        assertEquals(401584369, Hash33.of("W_X_key123", 5381));
        String qrsig = "15e082a4a278d09181b5e92941a34236a0e409a8f0ba0dc1624693ecfe581da40bf056db189fba9a3ecc43bfd177e5e16cf008508d69dd36fe7868a452f82551";
        assertEquals(pythonHash33(qrsig), Hash33.of(qrsig, 0));
    }

    private static long pythonHash33(String s) {
        BigInteger h = BigInteger.ZERO;
        for (char c : s.toCharArray()) h = h.shiftLeft(5).add(h).add(BigInteger.valueOf(c));
        return h.and(BigInteger.valueOf(0x7fffffffL)).longValue();
    }

    @Test
    void decodesSmallPtloginStylePng() throws Exception {
        // ptqrshow returns a ~111 px PNG with 3 px modules
        String content = "http://txz.qq.com/p?k=77y-zYYj9cmDcHqWexmKiK7tTmVuSHUR&f=716027609";
        BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 2));
        int scale = 3;
        BufferedImage img = new BufferedImage(m.getWidth() * scale, m.getHeight() * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.setRGB(x, y, m.get(x / scale, y / scale) ? 0x000000 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        assertEquals(content, QrImage.decode(png.toByteArray()));
    }
}
