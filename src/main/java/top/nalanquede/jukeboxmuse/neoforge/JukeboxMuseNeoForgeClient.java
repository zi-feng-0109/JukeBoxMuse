package top.nalanquede.jukeboxmuse.neoforge;

//? if neoforge {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
//? if >=1.20.5
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.client.ClientHooks;

/^* NeoForge client entry point: forwards client events to {@link ClientHooks}. Started by the common one before 20.5. ^/
//? if >=1.20.5
@Mod(value = JukeboxMuse.MODID, dist = Dist.CLIENT)
public final class JukeboxMuseNeoForgeClient {
    public JukeboxMuseNeoForgeClient(IEventBus modEventBus) {
        // Client setup runs after every @Mod constructor, so the common entry point has installed the services
        modEventBus.addListener((FMLClientSetupEvent e) -> ClientHooks.init());
        modEventBus.addListener((RegisterKeyMappingsEvent e) -> {
            //? if >=1.21.9
            e.registerCategory(ClientHooks.CATEGORY);
            e.register(ClientHooks.OPEN_KEY);
        });
        //? if >=1.20.5 {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientHooks.tick());
        //?} else {
        /^// With the class given: 20.4's bus cannot tell a lambda's event type when the event is a nested class
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.TickEvent.ClientTickEvent.class, e -> {
            if (e.phase == net.neoforged.neoforge.event.TickEvent.Phase.END) ClientHooks.tick();
        });
        ^///?}
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> ClientHooks.renderHud(e.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientHooks.disconnect());
    }
}
*///?}
