package top.nalanquede.kgmusic.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.nalanquede.kgmusic.common.Platform;

/** Vanilla container look drawn with fills, so it follows no texture size: panels, slot-style insets, text boxes. */
final class McGui {
    /** Container label colour (the "Inventory" text). */
    static final int LABEL = 0xFF404040;
    static final int LABEL_DIM = 0xFF707070;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    // One vanilla disc per platform, picked so the label colours tell them apart
    private static final ItemStack KUGOU_DISC = new ItemStack(Items.MUSIC_DISC_WAIT);
    private static final ItemStack NETEASE_DISC = new ItemStack(Items.MUSIC_DISC_BLOCKS);
    private static final ItemStack QQ_DISC = new ItemStack(Items.MUSIC_DISC_CAT);

    private McGui() {
    }

    static ItemStack disc(Platform platform) {
        return switch (platform) {
            case KUGOU -> KUGOU_DISC;
            case NETEASE -> NETEASE_DISC;
            case QQ -> QQ_DISC;
        };
    }

    /** Raised gray panel with the rounded black outline of container screens. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 2, y + 1, x + w - 3, y + 3, HIGHLIGHT);
        g.fill(x + 1, y + 2, x + 3, y + h - 3, HIGHLIGHT);
        g.fill(x + 3, y + h - 3, x + w - 2, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 2, SHADOW);
        g.fill(x + 2, y, x + w - 2, y + 1, OUTLINE);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, OUTLINE);
        g.fill(x, y + 2, x + 1, y + h - 2, OUTLINE);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, OUTLINE);
        g.fill(x + 1, y + 1, x + 2, y + 2, OUTLINE);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, OUTLINE);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, OUTLINE);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, OUTLINE);
    }

    /** Sunken area like an inventory slot: dark top-left edge, white bottom-right edge. */
    static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_SHADOW);
        g.fill(x + 1, y + 1, x + w, y + h, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT);
    }

    /** Black box with the gray border of a text field, for light text. */
    static void textBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000);
    }

    /** Container-style text: no shadow. */
    static void label(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    static void centeredLabel(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }
}
