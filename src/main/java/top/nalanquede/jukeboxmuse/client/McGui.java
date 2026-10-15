package top.nalanquede.jukeboxmuse.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
    static final WidgetSprites BUTTON = new WidgetSprites(
            JukeboxMuse.vanillaId("widget/button"),
            JukeboxMuse.vanillaId("widget/button_disabled"),
            JukeboxMuse.vanillaId("widget/button_highlighted"));

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
        *///?} else
        g.blitSprite(sprite, x, y, w, h);
    }

    /** The w×h region at (u, v) of a spriteW×spriteH GUI sprite, unscaled at (x, y). */
    static void spritePart(GuiGraphics g, ResourceLocation sprite, int spriteW, int spriteH, int u, int v, int x, int y, int w, int h) {
        //? if >=1.21.6 {
        /*g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, spriteW, spriteH, u, v, x, y, w, h);
        *///?} elif >=1.21.2 {
        /*g.blitSprite(RenderType::guiTextured, sprite, spriteW, spriteH, u, v, x, y, w, h);
        *///?} else
        g.blitSprite(sprite, spriteW, spriteH, u, v, x, y, w, h);
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
