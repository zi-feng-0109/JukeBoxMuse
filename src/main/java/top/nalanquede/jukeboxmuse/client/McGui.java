package top.nalanquede.jukeboxmuse.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.20.2
import net.minecraft.client.gui.components.WidgetSprites;
//? if <1.21.6
import net.minecraft.client.renderer.RenderType;
//? if >=1.21.6
/*import net.minecraft.client.renderer.RenderPipelines;*/
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.common.Platform;

/** Vanilla container look drawn with fills, so it follows no texture size: panels, slot-style insets, text boxes. */
final class McGui {
    /** Container label colour (the "Inventory" text). */
    static final int LABEL = 0xFF404040;
    static final int LABEL_DIM = 0xFF707070;

    /** The vanilla button sprites (AbstractButton.SPRITES is private in vanilla; only NeoForge widens it). */
    //? if >=1.20.2 {
    static final WidgetSprites BUTTON = new WidgetSprites(
            JukeboxMuse.vanillaId("widget/button"),
            JukeboxMuse.vanillaId("widget/button_disabled"),
            JukeboxMuse.vanillaId("widget/button_highlighted"));
    //?} else
    /*static final Sprites BUTTON = new Sprites("widget/button", "widget/button_disabled", "widget/button_highlighted");*/

    //? if <1.20.2 {
    /*/^* WidgetSprites before 1.20.2: the button's sprite for its state. ^/
    record Sprites(String enabled, String disabled, String focused) {
        ResourceLocation get(boolean active, boolean focused) {
            return JukeboxMuse.vanillaId(!active ? disabled : focused ? this.focused : enabled);
        }
    }

    // The regions of 1.20.1's textures standing in for the sprites below
    private static final ResourceLocation WIDGETS = JukeboxMuse.vanillaId("textures/gui/widgets.png");
    private static final ResourceLocation ICONS = JukeboxMuse.vanillaId("textures/gui/icons.png");
    private static final ResourceLocation TABS = JukeboxMuse.vanillaId("textures/gui/container/creative_inventory/tabs.png");

    /^* Draws a sprite name the mod uses from its old texture; false if it is not one of them. ^/
    private static boolean legacySprite(GuiGraphics g, String name, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        switch (name) {
            // 200×20 buttons, nine-sliced with 3 px borders like vanilla's (20 wide, 4 tall edges)
            case "widget/button_disabled" -> g.blitNineSliced(WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, 46);
            case "widget/button" -> g.blitNineSliced(WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, 66);
            case "widget/button_highlighted" -> g.blitNineSliced(WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, 86);
            case "hud/experience_bar_background" -> g.blit(ICONS, x, y, 0, 64, w, h);
            case "container/creative_inventory/scroller" -> g.blit(TABS, x, y, 232, 0, w, h);
            case "container/creative_inventory/scroller_disabled" -> g.blit(TABS, x, y, 244, 0, w, h);
            default -> {
                // Creative tabs: tab_top_(un)selected_N, 26×32 at u = (N-1)·26, v = 0 (unselected) or 32
                String prefix = "container/creative_inventory/tab_top_";
                if (!name.startsWith(prefix)) {
                    RenderSystem.disableBlend();
                    return false;
                }
                String rest = name.substring(prefix.length());
                boolean selected = rest.startsWith("selected_");
                int n = Integer.parseInt(rest.substring(rest.lastIndexOf('_') + 1));
                g.blit(TABS, x, y, (n - 1) * 26, selected ? 32 : 0, w, h);
            }
        }
        RenderSystem.disableBlend();
        return true;
    }
    *///?}

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

    /** A GUI sprite (textures/gui/sprites/), stretched or nine-sliced to w×h. */
    static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        //? if >=1.21.6 {
        /*g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
        *///?} elif >=1.21.2 {
        /*g.blitSprite(RenderType::guiTextured, sprite, x, y, w, h);
        *///?} elif >=1.20.2 {
        g.blitSprite(sprite, x, y, w, h);
        //?} else {
        /*if (!legacySprite(g, sprite.getPath(), x, y, w, h)) throw new IllegalArgumentException("No 1.20.1 texture for " + sprite);
        *///?}
    }

    /** The w×h region at (u, v) of a spriteW×spriteH GUI sprite, unscaled at (x, y). */
    static void spritePart(GuiGraphics g, ResourceLocation sprite, int spriteW, int spriteH, int u, int v, int x, int y, int w, int h) {
        //? if >=1.21.6 {
        /*g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, spriteW, spriteH, u, v, x, y, w, h);
        *///?} elif >=1.21.2 {
        /*g.blitSprite(RenderType::guiTextured, sprite, spriteW, spriteH, u, v, x, y, w, h);
        *///?} elif >=1.20.2 {
        g.blitSprite(sprite, spriteW, spriteH, u, v, x, y, w, h);
        //?} else {
        /*// Only the experience bar's progress is drawn in part; it is the row under the background in icons.png
        if (!sprite.getPath().equals("hud/experience_bar_progress")) throw new IllegalArgumentException("No 1.20.1 texture for " + sprite);
        RenderSystem.enableBlend();
        g.blit(ICONS, x, y, u, 69 + v, w, h);
        RenderSystem.disableBlend();
        *///?}
    }

    /** A whole texW×texH texture (not a sprite), scaled to w×h at (x, y), with transparency. */
    static void texture(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int texW, int texH) {
        //? if >=1.21.6 {
        /*g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, texW, texH, texW, texH);
        *///?} elif >=1.21.2 {
        /*g.blit(RenderType::guiTextured, texture, x, y, 0, 0, w, h, texW, texH, texW, texH);
        *///?} else {
        RenderSystem.enableBlend();
        g.blit(texture, x, y, w, h, 0, 0, texW, texH, texW, texH);
        RenderSystem.disableBlend();
        //?}
    }

    // 2D transforms of the GUI pose; from 1.21.6 it is a 2D matrix stack without a z axis

    static void push(GuiGraphics g) {
        //? if >=1.21.6 {
        /*g.pose().pushMatrix();
        *///?} else
        g.pose().pushPose();
    }

    static void pop(GuiGraphics g) {
        //? if >=1.21.6 {
        /*g.pose().popMatrix();
        *///?} else
        g.pose().popPose();
    }

    static void translate(GuiGraphics g, float x, float y) {
        //? if >=1.21.6 {
        /*g.pose().translate(x, y);
        *///?} else
        g.pose().translate(x, y, 0);
    }

    static void scale(GuiGraphics g, float scale) {
        //? if >=1.21.6 {
        /*g.pose().scale(scale, scale);
        *///?} else
        g.pose().scale(scale, scale, 1);
    }

    /** Clockwise on screen, like vanilla's Axis.ZP rotations. */
    static void rotate(GuiGraphics g, float degrees) {
        //? if >=1.21.6 {
        /*g.pose().rotate((float) Math.toRadians(degrees));
        *///?} else
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
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
