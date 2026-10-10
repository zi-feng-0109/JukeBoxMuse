package top.nalanquede.jukeboxmuse.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.client.ClientHooks;

/** NeoForge client entry point: forwards client events to {@link ClientHooks}. */
@Mod(value = JukeboxMuse.MODID, dist = Dist.CLIENT)
public final class JukeboxMuseNeoForgeClient {
    public JukeboxMuseNeoForgeClient(IEventBus modEventBus) {
        // Client setup runs after every @Mod constructor, so the common entry point has installed the services
        modEventBus.addListener((FMLClientSetupEvent e) -> ClientHooks.init());
        modEventBus.addListener((RegisterKeyMappingsEvent e) -> e.register(ClientHooks.OPEN_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientHooks.tick());
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> ClientHooks.renderHud(e.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientHooks.disconnect());
    }
}
