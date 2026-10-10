package top.nalanquede.kgmusic.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Composes an album cover into a vinyl record: the cover as a round label in the middle, grooved black vinyl
 * around it, a spindle hole in the centre and transparent corners. Pure java.awt so it runs off the render thread.
 */
final class VinylImage {
    static final int SIZE = 128;
    private static final double LABEL_RADIUS = 46;

    private VinylImage() {
    }

    /** SIZE×SIZE pixels in ARGB order, row by row. */
    static int[] compose(BufferedImage cover) {
        int label = (int) (LABEL_RADIUS * 2);
        BufferedImage scaled = new BufferedImage(label, label, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(cover, 0, 0, label, label, null);
        g.dispose();

        int[] out = new int[SIZE * SIZE];
        double c = (SIZE - 1) / 2.0;
        int labelOrigin = SIZE / 2 - label / 2;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double r = Math.hypot(x - c, y - c);
                if (r > SIZE / 2.0) continue;
                int rgb;
                if (r <= 3) {
                    rgb = 0x1A1A1A;
                } else if (r <= LABEL_RADIUS) {
                    rgb = scaled.getRGB(Math.min(label - 1, x - labelOrigin), Math.min(label - 1, y - labelOrigin)) & 0xFFFFFF;
                } else if (r <= LABEL_RADIUS + 1.5) {
                    rgb = 0x0A0A0A;
                } else {
                    // Grooves: every third ring a little lighter
                    int shade = (int) r % 3 == 0 ? 0x2A : 0x16;
                    rgb = shade << 16 | shade << 8 | shade;
                }
                // Soft outer edge
                int alpha = (int) Math.round(Math.min(1, SIZE / 2.0 - r) * 255);
                out[y * SIZE + x] = alpha << 24 | rgb;
            }
        }
        return out;
    }
}
