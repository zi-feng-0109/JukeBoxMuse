package top.nalanquede.jukeboxmuse.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.common.Song;

/**
 * Album covers as spinning vinyl records. Covers download and decode on a background thread, then become a
 * texture on the render thread. Until then, or when a cover cannot be loaded (no URL, network error, no
 * javax.imageio as on some Android launchers), the platform's vanilla music disc spins instead.
 */
public final class CoverArt {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TEXTURES = 12;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JukeboxMuse-Cover");
        t.setDaemon(true);
        return t;
    });
    // Render thread only. Access order, so the eldest entry is the least recently drawn cover.
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static int nextId;

    private static final class Entry {
        ResourceLocation texture;
    }

    private CoverArt() {
    }

    /** Starts loading url if it is not cached yet; returns the texture once it is ready, else null. */
    public static ResourceLocation texture(String url) {
        if (url == null || url.isEmpty()) return null;
        Entry entry = CACHE.get(url);
        if (entry == null) {
            Entry created = new Entry();
            CACHE.put(url, created);
            evict();
            LOADER.execute(() -> load(url, created));
            return null;
        }
        return entry.texture;
    }

    /** Draws song's cover as a record of size×size at (x, y), turned by degrees around its centre. */
    public static void renderDisc(GuiGraphics g, Song song, int x, int y, int size, float degrees) {
        ResourceLocation texture = texture(song.coverUrl());
        McGui.push(g);
        McGui.translate(g, x + size / 2f, y + size / 2f);
        McGui.rotate(g, degrees);
        if (texture != null) {
            McGui.texture(g, texture, -size / 2, -size / 2, size, size, VinylImage.SIZE, VinylImage.SIZE);
        } else {
            McGui.scale(g, size / 16f);
            g.renderItem(McGui.disc(song.platform()), -8, -8);
        }
        McGui.pop(g);
    }

    private static void load(String url, Entry entry) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400 || response.body().length > MAX_BYTES) return;
            BufferedImage cover = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (cover == null) return;
            int[] argb = VinylImage.compose(cover);
            Minecraft.getInstance().execute(() -> upload(entry, argb));
        } catch (Throwable e) {
            // Includes NoClassDefFoundError where javax.imageio is missing; the vanilla disc stays
            JukeboxMuse.LOGGER.debug("Cannot load cover {}: {}", url, e.toString());
        }
    }

    private static void upload(Entry entry, int[] argb) {
        // The entry may have been evicted while this cover was downloading
        if (!CACHE.containsValue(entry)) return;
        NativeImage image = new NativeImage(VinylImage.SIZE, VinylImage.SIZE, true);
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            //? if >=1.21.2 {
            /*image.setPixel(i % VinylImage.SIZE, i / VinylImage.SIZE, p);
            *///?} else {
            // NativeImage wants ABGR
            int abgr = p & 0xFF00FF00 | (p & 0xFF) << 16 | p >> 16 & 0xFF;
            image.setPixelRGBA(i % VinylImage.SIZE, i / VinylImage.SIZE, abgr);
            //?}
        }
        ResourceLocation id = JukeboxMuse.id("cover/" + nextId++);
        // Smooth (linear) scaling, without mipmaps
        //? if >=1.21.11 {
        /*DynamicTexture texture = new DynamicTexture(id::toString, image) {
            {
                sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(LINEAR);
            }
        };
        *///?} elif >=1.21.5 {
        /*DynamicTexture texture = new DynamicTexture(id::toString, image);
        texture.setFilter(true, false);
        *///?} else {
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        //?}
        Minecraft.getInstance().getTextureManager().register(id, texture);
        entry.texture = id;
    }

    //? if >=1.21.11 {
    /*// 26.3 moved the GPU texture types from blaze3d to renderpearl
    //? if >=26.3 {
    /^private static final com.mojang.renderpearl.api.textures.FilterMode LINEAR = com.mojang.renderpearl.api.textures.FilterMode.LINEAR;
    ^///?} else
    private static final com.mojang.blaze3d.textures.FilterMode LINEAR = com.mojang.blaze3d.textures.FilterMode.LINEAR;
    *///?}

    private static void evict() {
        Iterator<Entry> it = CACHE.values().iterator();
        while (CACHE.size() > MAX_TEXTURES && it.hasNext()) {
            Entry old = it.next();
            it.remove();
            if (old.texture != null) Minecraft.getInstance().getTextureManager().release(old.texture);
        }
    }
}
