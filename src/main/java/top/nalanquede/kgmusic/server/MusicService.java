package top.nalanquede.kgmusic.server;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import top.nalanquede.kgmusic.KgConfig;
import top.nalanquede.kgmusic.KgMusic;
import top.nalanquede.kgmusic.common.LoginMethod;
import top.nalanquede.kgmusic.common.Platform;
import top.nalanquede.kgmusic.common.QueueEntry;
import top.nalanquede.kgmusic.common.Song;
import top.nalanquede.kgmusic.kugou.KugouProvider;
import top.nalanquede.kgmusic.music.MusicException;
import top.nalanquede.kgmusic.music.MusicProvider;
import top.nalanquede.kgmusic.music.MusicProvider.LoginQr;
import top.nalanquede.kgmusic.netease.NeteaseApi;
import top.nalanquede.kgmusic.network.KgPayloads.*;
import top.nalanquede.kgmusic.qqmusic.QqMusicApi;

/**
 * Server-side song queue. All state is touched on the server thread only. Each platform's calls run on its own
 * background thread (sequential calls keep the request rate low); QR login polls run on separate threads because
 * WeChat's status check is a long poll. Results are posted back with server.execute.
 */
public final class MusicService {
    private static final long SEARCH_CACHE_MS = 2 * 60 * 1000;
    private static final long REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final long REFRESH_RETRY_MS = 60 * 60 * 1000;
    private static final long RISK_BACKOFF_MS = 10 * 60 * 1000;
    private static final long DEFAULT_DURATION_MS = 4 * 60 * 1000;
    private static final long QR_TIMEOUT_MS = 2 * 60 * 1000;
    private static final long QR_POLL_MS = 2000;
    private static final int QR_MAX_ERRORS = 3;
    private static final int PAGE_SIZE = 20;
    private static MusicService instance;

    private final MinecraftServer server;
    private final Map<Platform, MusicProvider> providers = new EnumMap<>(Platform.class);
    private final Map<Platform, ExecutorService> workers = new EnumMap<>(Platform.class);
    private final ExecutorService loginWorker = Executors.newCachedThreadPool(daemon("KgMusic-Login"));

    private final Deque<QueueEntry> queue = new ArrayDeque<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Map<String, CachedSearch> searchCache = new HashMap<>();
    private QueueEntry current;
    private String currentUrl = "";
    private MusicProvider.LyricText currentLyric = MusicProvider.LyricText.EMPTY;
    private long startMs;
    private boolean resolving;
    private final Set<Platform> refreshing = EnumSet.noneOf(Platform.class);
    private final Map<Platform, Long> nextRefreshMs = new EnumMap<>(Platform.class);
    private final Map<Platform, Long> blockedUntil = new EnumMap<>(Platform.class);

    // One QR login at a time; starting another replaces it. loginSeq invalidates results of replaced logins.
    private final Map<Platform, String> loginState = new EnumMap<>(Platform.class);
    private LoginMethod loginMethod;
    private LoginQr qr;
    private int loginSeq;
    private long qrStartedMs;
    private long qrNextPollMs;
    private int qrErrors;

    private record CachedSearch(long time, List<Song> songs) {
    }

    private record Resolved(String url, MusicProvider.LyricText lyric) {
    }

    private MusicService(MinecraftServer server) {
        this.server = server;
        Path dir = FMLPaths.CONFIGDIR.get().resolve(KgMusic.MODID);
        // account.json keeps its pre-multi-platform name so existing KuGou logins survive the update
        addProvider(new KugouProvider(dir.resolve("account.json")));
        addProvider(new NeteaseApi(dir.resolve("netease.json")));
        addProvider(new QqMusicApi(dir.resolve("qqmusic.json")));
    }

    private void addProvider(MusicProvider provider) {
        Platform p = provider.platform();
        providers.put(p, provider);
        workers.put(p, Executors.newSingleThreadExecutor(daemon("KgMusic-" + p.id())));
        loginState.put(p, provider.isLoggedIn() ? "已登录" : "未登录");
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    public static MusicService get() {
        return instance;
    }

    public static void start(MinecraftServer server) {
        instance = new MusicService(server);
    }

    public static void stop() {
        if (instance != null) {
            instance.workers.values().forEach(ExecutorService::shutdownNow);
            instance.loginWorker.shutdownNow();
            instance = null;
        }
    }

    // ---------- background calls ----------

    private interface Call<T> {
        T call(MusicProvider provider) throws MusicException;
    }

    private interface Result<T> {
        void accept(T value, MusicException error);
    }

    /** Runs call on the platform's thread, then delivers the result on the server thread. */
    private <T> void async(Platform platform, Call<T> call, Result<T> result) {
        runOn(workers.get(platform), platform, call, result);
    }

    /** Like async, but on a login thread so a long poll does not hold up searches. */
    private <T> void asyncLogin(Platform platform, Call<T> call, Result<T> result) {
        runOn(loginWorker, platform, call, result);
    }

    private <T> void runOn(ExecutorService executor, Platform platform, Call<T> call, Result<T> result) {
        if (System.currentTimeMillis() < blockedUntil.getOrDefault(platform, 0L)) {
            result.accept(null, new MusicException(platform.displayName() + "触发了风控，暂停调用，请几分钟后再试"));
            return;
        }
        MusicProvider provider = providers.get(platform);
        executor.execute(() -> {
            T value = null;
            MusicException error = null;
            try {
                provider.prepare();
                value = call.call(provider);
            } catch (MusicException e) {
                error = e;
            } catch (Exception e) {
                KgMusic.LOGGER.error("{} call failed", platform.id(), e);
                error = new MusicException("内部错误: " + e);
            }
            T v = value;
            MusicException err = error;
            server.execute(() -> {
                if (err != null && err.verificationRequired()) {
                    blockedUntil.put(platform, System.currentTimeMillis() + RISK_BACKOFF_MS);
                    notifyManagers(Component.literal("[点歌] " + platform.displayName() + "要求安全验证（风控），已暂停 10 分钟").withStyle(ChatFormatting.RED));
                }
                result.accept(v, err);
            });
        });
    }

    // ---------- tick ----------

    public void tick() {
        long now = System.currentTimeMillis();
        if (current != null && !resolving) {
            long duration = current.song().durationMs() > 0 ? current.song().durationMs() : DEFAULT_DURATION_MS;
            if (now - startMs >= duration + 2000) playNext();
        }
        if (qr != null && now - qrStartedMs > QR_TIMEOUT_MS) {
            endQr(qr.method().platform(), "二维码已超时，请重新扫码登录");
        } else if (qr != null && now >= qrNextPollMs) {
            pollQr();
        }
        for (MusicProvider provider : providers.values()) {
            maybeRefresh(provider, now);
        }
    }

    private void maybeRefresh(MusicProvider provider, long now) {
        Platform platform = provider.platform();
        if (refreshing.contains(platform) || !provider.isLoggedIn() || now - provider.lastRefreshMs() < REFRESH_INTERVAL_MS
                || now < nextRefreshMs.getOrDefault(platform, 0L)) {
            return;
        }
        refreshing.add(platform);
        async(platform, p -> {
            p.refresh();
            return null;
        }, (v, err) -> {
            refreshing.remove(platform);
            if (err != null) {
                // Retry in an hour rather than every tick
                nextRefreshMs.put(platform, System.currentTimeMillis() + REFRESH_RETRY_MS);
                KgMusic.LOGGER.warn("{} token refresh failed: {}", platform.id(), err.getMessage());
            } else {
                KgMusic.LOGGER.info("{} token refreshed", platform.id());
                syncAccounts();
            }
        });
    }

    // ---------- requests from players ----------

    public void search(ServerPlayer player, Platform platform, String keyword, int page) {
        String kw = keyword.trim();
        int pg = Math.max(1, page);
        if (kw.isEmpty()) return;
        if (!providers.get(platform).isLoggedIn()) {
            reply(player, new SearchResultS2C(platform, kw, pg, List.of(), "服务器还没有登录" + platform.displayName() + "账号，请联系管理员"));
            return;
        }
        String cacheKey = platform.id() + "\u0000" + kw + "\u0000" + pg;
        CachedSearch cached = searchCache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.time() < SEARCH_CACHE_MS) {
            reply(player, new SearchResultS2C(platform, kw, pg, cached.songs(), ""));
            return;
        }
        UUID id = player.getUUID();
        async(platform, p -> p.search(kw, pg, PAGE_SIZE), (songs, err) -> {
            if (err == null) {
                searchCache.values().removeIf(c -> System.currentTimeMillis() - c.time() > SEARCH_CACHE_MS);
                searchCache.put(cacheKey, new CachedSearch(System.currentTimeMillis(), songs));
            }
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) reply(p, new SearchResultS2C(platform, kw, pg, err == null ? songs : List.of(), err == null ? "" : err.getMessage()));
        });
    }

    public void request(ServerPlayer player, Song song) {
        String name = player.getGameProfile().getName();
        long now = System.currentTimeMillis();
        boolean manager = canManage(player);
        if (!providers.get(song.platform()).isLoggedIn()) {
            fail(player, "服务器还没有登录" + song.platform().displayName() + "账号");
            return;
        }
        Long last = lastRequest.get(player.getUUID());
        long cooldown = KgConfig.COOLDOWN_SECONDS.get() * 1000L;
        if (last != null && now - last < cooldown && !manager) {
            fail(player, "点歌太快了，请 " + ((cooldown - (now - last)) / 1000 + 1) + " 秒后再试");
            return;
        }
        long mine = queue.stream().filter(e -> e.requester().equals(name)).count();
        if (mine >= KgConfig.MAX_PER_PLAYER.get() && !manager) {
            fail(player, "你已经点了 " + mine + " 首在排队，等播完再点吧");
            return;
        }
        if (queue.size() >= KgConfig.MAX_QUEUE.get()) {
            fail(player, "队列已满");
            return;
        }
        boolean duplicate = current != null && current.song().sameTrack(song)
                || queue.stream().anyMatch(e -> e.song().sameTrack(song));
        if (duplicate) {
            fail(player, "这首歌已经在队列里了");
            return;
        }
        lastRequest.put(player.getUUID(), now);
        queue.addLast(new QueueEntry(song, name));
        broadcast(Component.literal("[点歌] " + name + " 点了《" + song.title() + "》（" + song.platform().displayName() + "）").withStyle(ChatFormatting.AQUA));
        syncQueue();
        if (current == null && !resolving) playNext();
    }

    public void skip(CommandSourceStack source) {
        if (current == null && !resolving) {
            source.sendFailure(Component.literal("现在没有在放歌"));
            return;
        }
        ServerPlayer player = source.getPlayer();
        boolean own = current != null && player != null && current.requester().equals(player.getGameProfile().getName());
        boolean manager = source.hasPermission(2) || player != null && canManage(player);
        if (!own && !manager) {
            source.sendFailure(Component.literal("只有管理员或点歌的人可以切歌"));
            return;
        }
        if (resolving) return;
        broadcast(Component.literal("[点歌] " + source.getTextName() + " 切掉了《" + current.song().title() + "》").withStyle(ChatFormatting.GRAY));
        playNext();
    }

    public void remove(ServerPlayer player, int index) {
        if (index < 0 || index >= queue.size()) return;
        QueueEntry target = new ArrayList<>(queue).get(index);
        if (!target.requester().equals(player.getGameProfile().getName()) && !canManage(player)) {
            fail(player, "只能删除自己点的歌");
            return;
        }
        Iterator<QueueEntry> it = queue.iterator();
        for (int i = 0; it.hasNext(); i++) {
            it.next();
            if (i == index) {
                it.remove();
                break;
            }
        }
        syncQueue();
    }

    public void clear(CommandSourceStack source) {
        queue.clear();
        current = null;
        currentUrl = "";
        PacketDistributor.sendToAllPlayers(StopS2C.INSTANCE);
        syncQueue();
        broadcast(Component.literal("[点歌] " + source.getTextName() + " 清空了点歌队列").withStyle(ChatFormatting.GRAY));
    }

    // ---------- playback ----------

    private void playNext() {
        QueueEntry next = queue.pollFirst();
        current = null;
        currentUrl = "";
        currentLyric = MusicProvider.LyricText.EMPTY;
        if (next == null) {
            PacketDistributor.sendToAllPlayers(StopS2C.INSTANCE);
            syncQueue();
            return;
        }
        resolving = true;
        syncQueue();
        String quality = KgConfig.QUALITY.get();
        async(next.song().platform(), p -> {
            String url = p.songUrl(next.song(), quality);
            MusicProvider.LyricText lyric;
            try {
                lyric = p.lyric(next.song());
            } catch (MusicException e) {
                lyric = MusicProvider.LyricText.EMPTY;
            }
            return new Resolved(url, lyric);
        }, (res, err) -> {
            resolving = false;
            if (err != null) {
                broadcast(Component.literal("[点歌] 《" + next.song().title() + "》无法播放：" + err.getMessage() + "，已跳过").withStyle(ChatFormatting.YELLOW));
                playNext();
                return;
            }
            current = next;
            currentUrl = res.url();
            currentLyric = res.lyric();
            startMs = System.currentTimeMillis();
            PacketDistributor.sendToAllPlayers(new PlayS2C(current, currentUrl, 0, currentLyric.lrc(), currentLyric.translation()));
            broadcast(Component.literal("[点歌] 正在播放《" + current.song().title() + "》（" + current.requester() + " 点的）").withStyle(ChatFormatting.GREEN));
        });
    }

    private void syncQueue() {
        PacketDistributor.sendToAllPlayers(new QueueS2C(List.copyOf(queue)));
    }

    /** Full state for a player who just joined or opened the screen. */
    public void sendState(ServerPlayer player) {
        reply(player, new QueueS2C(List.copyOf(queue)));
        if (current != null) {
            reply(player, new PlayS2C(current, currentUrl, System.currentTimeMillis() - startMs, currentLyric.lrc(), currentLyric.translation()));
        }
        reply(player, accountPayload(player));
    }

    // ---------- login ----------

    /** OPs, and the owner of a single-player world even without cheats, may manage the accounts. */
    public boolean canManage(ServerPlayer player) {
        return player.hasPermissions(2) || server.isSingleplayerOwner(player.getGameProfile());
    }

    /** Started from the account page: fetches a QR code for method, then polls it from tick(). */
    public void loginStart(ServerPlayer player, LoginMethod method) {
        if (!canManage(player)) {
            fail(player, "只有管理员可以登录音乐账号");
            return;
        }
        if (method == loginMethod) {
            // Already fetching or showing this code
            syncAccounts();
            return;
        }
        if (loginMethod != null && loginMethod.platform() != method.platform()) {
            loginState.put(loginMethod.platform(), "已取消扫码");
        }
        Platform platform = method.platform();
        int seq = ++loginSeq;
        loginMethod = method;
        qr = null;
        loginState.put(platform, "正在获取二维码...");
        syncAccounts();
        asyncLogin(platform, p -> p.loginQr(method), (code, err) -> {
            if (seq != loginSeq) return;
            if (err != null) {
                endQr(platform, "获取二维码失败：" + err.getMessage());
                return;
            }
            qr = code;
            qrStartedMs = System.currentTimeMillis();
            qrNextPollMs = qrStartedMs + QR_POLL_MS;
            qrErrors = 0;
            loginState.put(platform, method.scanHint());
            syncAccounts();
        });
    }

    private void pollQr() {
        LoginQr code = qr;
        int seq = loginSeq;
        Platform platform = code.method().platform();
        qrNextPollMs = Long.MAX_VALUE;
        asyncLogin(platform, p -> p.loginCheck(code), (status, err) -> {
            if (seq != loginSeq) return;
            if (err != null) {
                KgMusic.LOGGER.warn("{} QR check failed: {}", platform.id(), err.getMessage());
                if (++qrErrors >= QR_MAX_ERRORS || err.verificationRequired()) {
                    endQr(platform, "登录失败：" + err.getMessage());
                    return;
                }
            } else {
                qrErrors = 0;
                switch (status) {
                    case EXPIRED -> {
                        endQr(platform, "二维码已过期，请重新扫码登录");
                        return;
                    }
                    case REFUSED -> {
                        endQr(platform, "已在手机上取消登录");
                        return;
                    }
                    case SUCCESS -> {
                        String nickname = providers.get(platform).nickname();
                        endQr(platform, "登录成功：" + nickname);
                        notifyManagers(Component.literal("[点歌] " + platform.displayName() + "账号登录成功：" + nickname).withStyle(ChatFormatting.GREEN));
                        return;
                    }
                    case SCANNED -> {
                        loginState.put(platform, "已扫码，请在手机上确认登录");
                        syncAccounts();
                    }
                    case WAITING -> {
                    }
                }
            }
            qrNextPollMs = System.currentTimeMillis() + QR_POLL_MS;
        });
    }

    private void endQr(Platform platform, String state) {
        loginSeq++;
        loginMethod = null;
        qr = null;
        loginState.put(platform, state);
        syncAccounts();
    }

    public void logout(ServerPlayer player, Platform platform) {
        if (!canManage(player)) {
            fail(player, "只有管理员可以退出音乐账号");
            return;
        }
        providers.get(platform).logout();
        loginState.put(platform, "已退出登录");
        syncAccounts();
        player.sendSystemMessage(Component.literal("[点歌] 已退出" + platform.displayName() + "账号"));
    }

    public String statusLine() {
        List<String> accounts = new ArrayList<>();
        for (MusicProvider p : providers.values()) {
            accounts.add(p.platform().displayName() + "：" + (p.isLoggedIn() ? "已登录 " + p.nickname() + (p.vip() ? "（VIP）" : "") : "未登录"));
        }
        String now = current != null ? "正在播放《" + current.song().title() + "》" : resolving ? "正在加载下一首" : "空闲";
        return String.join("，", accounts) + "；" + now + "；排队 " + queue.size() + " 首";
    }

    /** /kgmusic debug: runs a search and logs the raw response. */
    public void debugSearch(CommandSourceStack source, Platform platform, String keyword) {
        async(platform, p -> p.search(keyword, 1, 5), (songs, err) -> {
            if (err != null) {
                source.sendFailure(Component.literal("搜索失败：" + err.getMessage()));
                return;
            }
            source.sendSuccess(() -> Component.literal("搜索到 " + songs.size() + " 首（原始响应已写入日志）"), false);
            for (Song s : songs) {
                source.sendSuccess(() -> Component.literal(" - " + s.title() + " | " + s.durationMs() / 1000 + "s | id=" + s.id() + (s.vip() ? " | VIP" : "")), false);
            }
        });
    }

    private AccountS2C accountPayload(ServerPlayer player) {
        boolean manager = canManage(player);
        List<AccountInfo> accounts = new ArrayList<>();
        for (MusicProvider p : providers.values()) {
            Platform platform = p.platform();
            String qrContent = manager && qr != null && qr.method().platform() == platform ? qr.content() : "";
            accounts.add(new AccountInfo(platform, p.isLoggedIn(), p.nickname(), p.vip(), qrContent, manager ? loginState.get(platform) : ""));
        }
        return new AccountS2C(manager, accounts);
    }

    /** Everyone needs to know which platforms are usable; managers also get the QR code and login progress. */
    private void syncAccounts() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            reply(p, accountPayload(p));
        }
    }

    // ---------- helpers ----------

    private static void reply(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[点歌] " + message).withStyle(ChatFormatting.RED));
    }

    private void broadcast(Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private void notifyManagers(Component message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (canManage(p)) p.sendSystemMessage(message);
        }
        KgMusic.LOGGER.info(message.getString());
    }
}
