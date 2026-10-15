package top.nalanquede.jukeboxmuse;

import java.nio.file.Path;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;

import top.nalanquede.jukeboxmuse.config.ServerSettings;
import top.nalanquede.jukeboxmuse.music.MusicLog;
import top.nalanquede.jukeboxmuse.platform.LoaderServices;

/** Loader-independent constants and setup. Each loader's entry point calls {@link #init} first. */
public final class JukeboxMuse {
    public static final String MODID = "jukeboxmuse";
    public static final Logger LOGGER = LogUtils.getLogger();

    private JukeboxMuse() {
    }

    public static void init(LoaderServices services) {
        LoaderServices.install(services);
        MusicLog.setDebug(() -> ServerSettings.current().debugLog());
    }

    /** jukeboxmuse:path */
    public static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
        //?} else
        /*return new ResourceLocation(MODID, path);*/
    }

    /** minecraft:path */
    public static ResourceLocation vanillaId(String path) {
        //? if >=1.21 {
        return ResourceLocation.withDefaultNamespace(path);
        //?} else
        /*return new ResourceLocation(path);*/
    }

    /** config/jukeboxmuse/, holding the settings and the logged-in accounts. */
    public static Path configDir() {
        return LoaderServices.get().configDir().resolve(MODID);
    }
}
