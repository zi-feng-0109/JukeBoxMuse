package top.nalanquede.kgmusic;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import top.nalanquede.kgmusic.network.KgNetwork;

@Mod(KgMusic.MODID)
public class KgMusic {
    public static final String MODID = "kgmusic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KgMusic(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, KgConfig.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, KgConfig.CLIENT_SPEC);
        modEventBus.addListener(KgNetwork::register);
    }

    public static boolean debugLogging() {
        try {
            return KgConfig.DEBUG_LOG.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet
            return true;
        }
    }
}
