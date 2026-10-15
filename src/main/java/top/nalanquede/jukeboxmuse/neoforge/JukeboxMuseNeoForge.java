package top.nalanquede.jukeboxmuse.neoforge;

//? if neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import top.nalanquede.jukeboxmuse.JukeboxMuse;

/^* NeoForge entry point. ^/
@Mod(JukeboxMuse.MODID)
public final class JukeboxMuseNeoForge {
    public JukeboxMuseNeoForge(IEventBus modEventBus) {
        JukeboxMuse.init(new NeoForgeServices());
        modEventBus.addListener(NeoForgeNetwork::register);
        //? if <1.20.5 {
        /^if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) new JukeboxMuseNeoForgeClient(modEventBus);
        ^///?}
    }
}
*///?}
