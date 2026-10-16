package top.nalanquede.jukeboxmuse.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A vanilla button showing an item instead of text. A selected button stays highlighted and ignores clicks. */
final class IconButton extends AbstractButton {
    private final ItemStack icon;
    private final boolean selected;
    private final Runnable action;

    IconButton(int x, int y, int w, int h, ItemStack icon, Component tooltip, boolean selected, boolean active, Runnable action) {
        super(x, y, w, h, tooltip);
        this.icon = icon;
        this.selected = selected;
        this.active = active;
        this.action = action;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    //? if >=1.21.9 {
    /*public void onPress(net.minecraft.client.input.InputWithModifiers input) {
    *///?} else
    public void onPress() {
        if (!selected) action.run();
    }

    @Override
    //? if >=26.1 {
    /*protected void extractContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    *///?} elif >=1.21.11 {
    /*protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    *///?} else
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // From 1.21.2 the sprite's render type sets up blending itself
        //? if <1.21.2 {
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        //?}
        McGui.sprite(g, McGui.BUTTON.get(active, selected || isHoveredOrFocused()), getX(), getY(), getWidth(), getHeight());
        g.renderItem(icon, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
        if (!active) {
            // Dim the item of a platform that cannot be used. Before 1.21.6 items draw at z 150+, so lift the
            // overlay above them; from then on later draws simply go on top
            //? if <1.21.6 {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            //?}
            g.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, 0x99303030);
            //? if <1.21.6
            g.pose().popPose();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
