package top.nalanquede.jukeboxmuse.qqmusic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import top.nalanquede.jukeboxmuse.music.MusicException;

/**
 * QQ's ptlogin only hands out its login QR code as a PNG. Decoding it on the server lets the client redraw it
 * from text like every other platform's code.
 */
final class QrImage {
    private QrImage() {
    }

    static String decode(byte[] image) throws MusicException {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(image));
            if (img == null) throw new MusicException("二维码图片无法识别");
            int[] pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(img.getWidth(), img.getHeight(), pixels)));
            return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
        } catch (MusicException e) {
            throw e;
        } catch (Exception e) {
            throw new MusicException("二维码图片无法识别: " + e);
        }
    }
}
