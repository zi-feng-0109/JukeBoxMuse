package top.nalanquede.jukeboxmuse.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import top.nalanquede.jukeboxmuse.common.Song;
import top.nalanquede.jukeboxmuse.network.MusePayloads.RequestSongC2S;

/**
 * One search result drawn as a full-width vanilla button, like a villager trade: disc, title, state, duration.
 * Clicking requests the song; the row greys out while the song is pending, queued or playing.
 */
final class SongRow extends AbstractButton {
    private final Song song;

    SongRow(int x, int y, int w, int h, Song song) {
        super(x, y, w, h, Component.literal(song.title()));
        this.song = song;
    }

    @Override
    public void onPress() {
        if (ClientState.trackState(song) != ClientState.TrackState.NONE) return;
        ClientState.markPending(song);
        PacketDistributor.sendToServer(new RequestSongC2S(song));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        ClientState.TrackState state = ClientState.trackState(song);
        active = state == ClientState.TrackState.NONE;

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        g.blitSprite(SPRITES.get(active, isHoveredOrFocused()), getX(), getY(), getWidth(), getHeight());
        g.renderItem(McGui.disc(song.platform()), getX() + 2, getY() + (getHeight() - 16) / 2);

        int textY = getY() + (getHeight() - 8) / 2;
        int right = getX() + getWidth() - 5;
        String duration = time(song.durationMs());
        right -= font.width(duration);
        g.drawString(font, duration, right, textY, 0xFFA0A0A0);

        String status = switch (state) {
            case PENDING -> "请求中…";
            case QUEUED -> "✔ 已点";
            case PLAYING -> "♪ 播放中";
            case NONE -> song.vip() ? "VIP" : "";
        };
        int statusColor = switch (state) {
            case PENDING -> 0xFFA0A0A0;
            case QUEUED -> 0xFF55FF55;
            case PLAYING -> 0xFF55FFFF;
            case NONE -> 0xFFFFAA00;
        };
        if (!status.isEmpty()) {
            right -= font.width(status) + 8;
            g.drawString(font, status, right, textY, statusColor);
        }

        int textX = getX() + 22;
        String title = font.plainSubstrByWidth(song.title(), right - 6 - textX);
        g.drawString(font, title, textX, textY, active ? 0xFFFFFFFF : 0xFFC0C0C0);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    static String time(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
