package top.nalanquede.jukeboxmuse.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import top.nalanquede.jukeboxmuse.MuseConfig;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.network.MusePayloads.HelloC2S;

@Mod(value = JukeboxMuse.MODID, dist = Dist.CLIENT)
public final class MuseClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.jukeboxmuse.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.jukeboxmuse");

    public MuseClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(MuseClient::onRegisterKeys);
        NeoForge.EVENT_BUS.addListener(MuseClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MuseClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(MuseClient::onLoggingOut);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                // Ask the server for fresh queue/account state, then open
                PacketDistributor.sendToServer(HelloC2S.INSTANCE);
                mc.setScreen(new MusicScreen());
            }
        }
    }

    /** Current lyric line at the top of the screen. */
    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!MuseConfig.LYRIC_HUD.get() || ClientState.nowPlaying == null || mc.options.hideGui || mc.screen instanceof MusicScreen) return;
        Lyrics lyrics = ClientState.lyrics;
        int idx = lyrics.indexAt(ClientState.positionMs());
        if (idx < 0) return;
        Lyrics.Line line = lyrics.lines().get(idx);
        GuiGraphics g = event.getGuiGraphics();
        int w = Math.max(mc.font.width(line.text()), line.translated() ? mc.font.width(line.translation()) : 0);
        int x = (g.guiWidth() - w) / 2;
        int y = 4;
        // A translated line gets a second row under the original
        g.fill(x - 4, y - 2, x + w + 4, y + (line.translated() ? 20 : 10), 0x80000000);
        g.drawCenteredString(mc.font, line.text(), g.guiWidth() / 2, y, 0xFF55FFFF);
        if (line.translated()) g.drawCenteredString(mc.font, line.translation(), g.guiWidth() / 2, y + 10, 0xFFDDDDDD);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AudioPlayer.stop();
        ClientState.reset();
        QrCode.update("");
    }
}
