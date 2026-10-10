package top.nalanquede.jukeboxmuse;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import top.nalanquede.jukeboxmuse.music.MusicLog;
import top.nalanquede.jukeboxmuse.network.MuseNetwork;

@Mod(JukeboxMuse.MODID)
public class JukeboxMuse {
    public static final String MODID = "jukeboxmuse";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JukeboxMuse(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, MuseConfig.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, MuseConfig.CLIENT_SPEC);
        modEventBus.addListener(MuseNetwork::register);
        MusicLog.setDebug(JukeboxMuse::debugLogging);
    }

    public static boolean debugLogging() {
        try {
            return MuseConfig.DEBUG_LOG.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet
            return true;
        }
    }
}
