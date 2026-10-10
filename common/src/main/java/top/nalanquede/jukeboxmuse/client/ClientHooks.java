package top.nalanquede.jukeboxmuse.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.config.ClientSettings;
import top.nalanquede.jukeboxmuse.lyrics.Lyrics;
import top.nalanquede.jukeboxmuse.network.MusePayloads.HelloC2S;
import top.nalanquede.jukeboxmuse.platform.LoaderServices;

/** Client behaviour the loaders' events forward to. */
public final class ClientHooks {
    /** Registered by each loader. */
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.jukeboxmuse.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.jukeboxmuse");

    private ClientHooks() {
    }

    /** At client setup, once the loader services are installed. */
    public static void init() {
        ClientSettings.load(JukeboxMuse.configDir().resolve("client.json"));
    }

    /** After every client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                // Ask the server for fresh queue/account state, then open
                LoaderServices.get().sendToServer(HelloC2S.INSTANCE);
                mc.setScreen(new MusicScreen());
            }
        }
    }

    /** After the HUD is drawn: the current lyric line (and its translation) at the top of the screen. */
    public static void renderHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientSettings.get().lyricHud() || ClientState.nowPlaying == null || mc.options.hideGui || mc.screen instanceof MusicScreen) return;
        Lyrics lyrics = ClientState.lyrics;
        int idx = lyrics.indexAt(ClientState.positionMs());
        if (idx < 0) return;
        Lyrics.Line line = lyrics.lines().get(idx);
        int w = Math.max(mc.font.width(line.text()), line.translated() ? mc.font.width(line.translation()) : 0);
        int x = (g.guiWidth() - w) / 2;
        int y = 4;
        // A translated line gets a second row under the original
        g.fill(x - 4, y - 2, x + w + 4, y + (line.translated() ? 20 : 10), 0x80000000);
        g.drawCenteredString(mc.font, line.text(), g.guiWidth() / 2, y, 0xFF55FFFF);
        if (line.translated()) g.drawCenteredString(mc.font, line.translation(), g.guiWidth() / 2, y + 10, 0xFFDDDDDD);
    }

    /** When leaving a world or server. */
    public static void disconnect() {
        AudioPlayer.stop();
        ClientState.reset();
        QrCode.update("");
    }
}
