package top.nalanquede.kgmusic.client;

import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import net.minecraft.client.gui.GuiGraphics;
import top.nalanquede.kgmusic.KgMusic;

/** The KuGou login QR code, drawn module by module with GuiGraphics.fill (no texture needed). */
public final class QrCode {
    private static String content = "";
    private static BitMatrix matrix;

    private QrCode() {
    }

    public static void update(String newContent) {
        if (newContent.equals(content)) return;
        content = newContent;
        matrix = null;
        if (newContent.isEmpty()) return;
        try {
            matrix = new QRCodeWriter().encode(newContent, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
        } catch (Exception e) {
            KgMusic.LOGGER.warn("Cannot encode QR code: {}", e.toString());
        }
    }

    public static boolean present() {
        return matrix != null;
    }

    /** Draws the code centred in a size×size square at (x, y). */
    public static void render(GuiGraphics g, int x, int y, int size) {
        if (matrix == null) return;
        int modules = matrix.getWidth();
        int scale = Math.max(1, size / modules);
        int drawn = scale * modules;
        int ox = x + (size - drawn) / 2;
        int oy = y + (size - drawn) / 2;
        g.fill(ox, oy, ox + drawn, oy + drawn, 0xFFFFFFFF);
        for (int my = 0; my < modules; my++) {
            for (int mx = 0; mx < modules; mx++) {
                if (matrix.get(mx, my)) {
                    g.fill(ox + mx * scale, oy + my * scale, ox + (mx + 1) * scale, oy + (my + 1) * scale, 0xFF000000);
                }
            }
        }
    }
}
