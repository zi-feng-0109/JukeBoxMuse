package top.nalanquede.jukeboxmuse.test;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.sun.net.httpserver.HttpServer;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import top.nalanquede.jukeboxmuse.client.ClientHooks;
import top.nalanquede.jukeboxmuse.client.ClientState;
import top.nalanquede.jukeboxmuse.client.CoverArt;
import top.nalanquede.jukeboxmuse.client.MusicScreen;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.QueueEntry;
import top.nalanquede.jukeboxmuse.common.Song;
import top.nalanquede.jukeboxmuse.lyrics.Lyrics;
import top.nalanquede.jukeboxmuse.network.MusePayloads.AccountInfo;

/**
 * Drives the music screen in a real client (./gradlew :<version>-fabric:runClientGameTest) and takes screenshots of
 * every page, for checking the rendering on each Minecraft version. Uses fake client state, so it needs no logged-in
 * account; covers come from a local HTTP server. Written against APIs that are the same on every supported version
 * (private screen members through reflection), so it has no Stonecutter conditions.
 */
public final class MuseClientGameTest implements FabricClientGameTest {
    private static final int GLFW_MOUSE_BUTTON_LEFT = 0;

    @Override
    public void runTest(ClientGameTestContext context) {
        HttpServer covers = startCoverServer();
        String coverUrl = "http://127.0.0.1:" + covers.getAddress().getPort() + "/cover.png";
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();

            checkKeyMapping(context);

            // The open key works in game, and the server answers the screen's hello
            context.getInput().pressKey(ClientHooks.OPEN_KEY);
            context.waitForScreen(MusicScreen.class);
            context.waitTicks(10);
            context.takeScreenshot("jukeboxmuse-01-opened");
            context.setScreen(() -> null);

            context.runOnClient(mc -> fillState(coverUrl));
            context.setScreen(MusicScreen::new);
            context.waitForScreen(MusicScreen.class);

            String[] tabs = {"now", "search", "queue", "account"};
            for (int i = 0; i < tabs.length; i++) {
                clickTab(context, i);
                if (i == 0) {
                    // The record spins once the cover is downloaded and uploaded as a texture
                    context.waitFor(mc -> CoverArt.texture(coverUrl) != null, 200);
                }
                context.waitTicks(5);
                context.takeScreenshot("jukeboxmuse-02-tab-" + tabs[i]);
            }

            // Scrolling the queue (10 entries, 7 rows), with the cursor on the list
            clickTab(context, 2);
            moveToPanel(context, 0.5, 0.5);
            context.getInput().scroll(-1);
            context.waitTicks(3);
            context.getInput().scroll(-1);
            context.waitTicks(3);
            context.takeScreenshot("jukeboxmuse-03-queue-scrolled");

            // Tooltip of a tab
            clickTab(context, 1);
            hoverTab(context, 0);
            context.waitTicks(3);
            context.takeScreenshot("jukeboxmuse-04-tab-tooltip");

            // The open key closes the screen; not on the search page, where it types into the focused search box
            clickTab(context, 0);
            context.getInput().pressKey(ClientHooks.OPEN_KEY);
            context.waitFor(mc -> mc.screen == null, 40);

            // The lyric line on the HUD
            context.waitTicks(5);
            context.takeScreenshot("jukeboxmuse-05-hud-lyrics");

            context.runOnClient(mc -> ClientState.reset());
        } finally {
            covers.stop(0);
        }
    }

    /** OPEN_KEY is registered and its category has a translation (a String before 1.21.9, a Category since). */
    private static void checkKeyMapping(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            if (!Arrays.asList(mc.options.keyMappings).contains(ClientHooks.OPEN_KEY)) {
                throw new AssertionError("Open key is not registered");
            }
            String label;
            String key;
            try {
                Object category = KeyMapping.class.getMethod("getCategory").invoke(ClientHooks.OPEN_KEY);
                if (category instanceof String s) {
                    key = s;
                    label = I18n.get(s);
                } else {
                    Component c = (Component) category.getClass().getMethod("label").invoke(category);
                    key = c.getContents().toString();
                    label = c.getString();
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            if (label.isEmpty() || label.startsWith("key.")) {
                throw new AssertionError("Key category has no translation: " + key + " -> " + label);
            }
            if (!I18n.exists(ClientHooks.OPEN_KEY.getName())) {
                throw new AssertionError("Open key has no translation: " + ClientHooks.OPEN_KEY.getName());
            }
        });
    }

    private static void fillState(String coverUrl) {
        Map<Platform, AccountInfo> accounts = new EnumMap<>(Platform.class);
        accounts.put(Platform.KUGOU, new AccountInfo(Platform.KUGOU, true, "测试账号", false, "", ""));
        accounts.put(Platform.NETEASE, new AccountInfo(Platform.NETEASE, true, "Test VIP", true, "", ""));
        // QQ stays logged out: its platform button is disabled and dimmed
        accounts.put(Platform.QQ, new AccountInfo(Platform.QQ, false, "", false, "", ""));
        ClientState.accounts = accounts;
        ClientState.canManage = true;

        List<Song> results = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            results.add(song(Platform.KUGOU, "r" + i, "搜索结果 " + i, i % 2 == 0 ? coverUrl : ""));
        }
        ClientState.searchPlatform = Platform.KUGOU;
        ClientState.searchKeyword = "测试";
        ClientState.searchPage = 1;
        ClientState.searchResults = results;
        ClientState.searchError = "";

        List<QueueEntry> queue = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            queue.add(new QueueEntry(song(i % 2 == 0 ? Platform.NETEASE : Platform.KUGOU, "q" + i, "排队歌曲 " + i, ""), "Player" + i));
        }
        ClientState.queue = queue;

        // Search result 2 is in the queue, result 4 is playing
        queue.set(0, new QueueEntry(results.get(1), "Player0"));
        ClientState.nowPlaying = new QueueEntry(song(Platform.KUGOU, "r4", "搜索结果 4", coverUrl), "Player0");
        ClientState.lyrics = Lyrics.parse("""
                [00:00.00]第一句歌词
                [00:01.00]第二句歌词，现在唱到这里
                [00:30.00]第三句歌词
                [00:40.00]第四句歌词
                """, """
                [00:00.00]First line
                [00:01.00]Second line, sung now
                [00:30.00]Third line
                """);
        ClientState.startedAtMs = System.currentTimeMillis() - 5_000;
    }

    private static Song song(Platform platform, String id, String name, String coverUrl) {
        return new Song(platform, id, "", 0, 0, name, "测试歌手", "测试专辑", 215_000, false, coverUrl);
    }

    private static void clickTab(ClientGameTestContext context, int index) {
        hoverTab(context, index);
        context.getInput().pressMouse(GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    /** Moves the cursor onto the index-th tab, using the screen's own layout. */
    private static void hoverTab(ClientGameTestContext context, int index) {
        double[] pos = context.computeOnClient(mc -> {
            MusicScreen screen = (MusicScreen) mc.screen;
            int x = (int) invoke(screen, "tabX", index) + 13;
            int y = (int) get(screen, "top") - 14;
            return new double[]{x, y};
        });
        moveToGui(context, pos[0], pos[1]);
    }

    /** Moves the cursor to a point of the panel, as fractions of its size. */
    private static void moveToPanel(ClientGameTestContext context, double fx, double fy) {
        double[] pos = context.computeOnClient(mc -> {
            MusicScreen screen = (MusicScreen) mc.screen;
            int left = (int) get(screen, "left");
            int top = (int) get(screen, "top");
            return new double[]{left + 340 * fx, top + 210 * fy};
        });
        moveToGui(context, pos[0], pos[1]);
    }

    private static void moveToGui(ClientGameTestContext context, double x, double y) {
        double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
        context.getInput().setCursorPos(x * scale, y * scale);
        context.waitTick();
    }

    private static Object invoke(Object target, String name, int arg) {
        try {
            Method m = target.getClass().getDeclaredMethod(name, int.class);
            m.setAccessible(true);
            return m.invoke(target, arg);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object get(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Serves a generated 300×300 album cover at /cover.png. */
    private static HttpServer startCoverServer() {
        try {
            BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setPaint(new GradientPaint(0, 0, new Color(0xE0405A), 300, 300, new Color(0x3050E0)));
            g.fillRect(0, 0, 300, 300);
            g.setColor(Color.WHITE);
            g.fillRect(0, 140, 300, 20);
            g.dispose();
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            byte[] bytes = png.toByteArray();

            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/cover.png", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new AssertionError("Cannot start the cover server", e);
        }
    }
}
