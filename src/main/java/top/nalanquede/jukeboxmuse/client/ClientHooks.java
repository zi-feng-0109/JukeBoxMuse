package top.nalanquede.jukeboxmuse.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.config.ClientSettings;
import top.nalanquede.jukeboxmuse.lyrics.Lyrics;
import top.nalanquede.jukeboxmuse.network.MusePayloads.HelloC2S;
import top.nalanquede.jukeboxmuse.platform.LoaderServices;

/** Client behaviour the loaders' events forward to. */
public final class ClientHooks {
    //? if >=1.21.9 {
    /*// The mod's section of the controls screen; registered by each loader
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(JukeboxMuse.id("main"));
    *///?}
    /** Registered by each loader. */
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.jukeboxmuse.open", /*? if >=26.3 {*//*InputConstants.Type.KEYBOARD*//*?} else {*/InputConstants.Type.KEYSYM/*?}*/, InputConstants.KEY_K,
            /*? if >=1.21.9 {*//*CATEGORY*//*?} else {*/"key.categories.jukeboxmuse"/*?}*/);

    private ClientHooks() {
    }

    /** At client setup, once the loader services are installed. */
    public static void init() {
        ClientSettings.load(JukeboxMuse.configDir().resolve("client.json"));
        JukeboxMuse.LOGGER.info("Jukebox Muse client ready");
    }

    /** After every client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (McGui.screen(mc) == null && mc.player != null) {
                // Ask the server for fresh queue/account state, then open
                LoaderServices.get().sendToServer(HelloC2S.INSTANCE);
                McGui.setScreen(mc, new MusicScreen());
            }
        }
        ScreenshotTour.tick(mc);
    }

    /** After the HUD is drawn: the current lyric line (and its translation) at the top of the screen. */
    public static void renderHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientSettings.get().lyricHud() || ClientState.nowPlaying == null || McGui.hudHidden(mc) || McGui.screen(mc) instanceof MusicScreen) return;
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
