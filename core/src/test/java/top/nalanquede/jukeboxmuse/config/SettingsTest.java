package top.nalanquede.jukeboxmuse.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {
    @TempDir
    Path dir;

    @Test
    void missingFileGetsDefaultsAndIsCreatedWithComments() throws Exception {
        Path file = dir.resolve("server.json");
        assertEquals(ServerSettings.DEFAULTS, ServerSettings.load(file));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("// 每个玩家两次点歌之间"));
    }

    @Test
    void writtenFileReadsBackAndBadValuesAreClamped() throws Exception {
        Path file = dir.resolve("server.json");
        Files.writeString(file, "// edited\n{ \"cooldownSeconds\": 99999, \"quality\": \"flac\", \"debugLog\": false }", StandardCharsets.UTF_8);
        ServerSettings s = ServerSettings.load(file);
        assertEquals(3600, s.cooldownSeconds());
        assertEquals("128", s.quality());
        assertFalse(s.debugLog());
        assertEquals(ServerSettings.DEFAULTS.maxQueueSize(), s.maxQueueSize());
        // The normalised file (with its comments) loads to the same values
        assertEquals(s, ServerSettings.load(file));
    }

    @Test
    void clientSettingsSaveImmediately() {
        Path file = dir.resolve("client.json");
        ClientSettings.load(file).setVolume(0.25);
        ClientSettings.get().setLyricHud(false);
        ClientSettings reloaded = ClientSettings.load(file);
        assertEquals(0.25, reloaded.volume());
        assertFalse(reloaded.lyricHud());
    }
}
