package top.nalanquede.jukeboxmuse.client;

import java.util.Arrays;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.nalanquede.jukeboxmuse.JukeboxMuse;
import top.nalanquede.jukeboxmuse.common.LoginMethod;
import top.nalanquede.jukeboxmuse.common.Platform;
import top.nalanquede.jukeboxmuse.common.QueueEntry;
import top.nalanquede.jukeboxmuse.common.Song;
import top.nalanquede.jukeboxmuse.config.ClientSettings;
import top.nalanquede.jukeboxmuse.lyrics.Lyrics;
import top.nalanquede.jukeboxmuse.network.MusePayloads.*;
import top.nalanquede.jukeboxmuse.platform.LoaderServices;

/**
 * The song request GUI in vanilla container style: creative-inventory tabs with item icons above a gray panel.
 * Tabs: Now playing (jukebox) / Search (spyglass) / Queue (book) / Account (name tag, managers only).
 */
public class MusicScreen extends Screen {
    private enum Tab {
        NOW("正在播放", Items.JUKEBOX),
        SEARCH("搜索点歌", Items.SPYGLASS),
        QUEUE("队列", Items.BOOK),
        ACCOUNT("账号", Items.NAME_TAG);

        final String title;
        final ItemStack icon;

        Tab(String title, Item icon) {
            this.title = title;
            this.icon = new ItemStack(icon);
        }
    }

    private static final int W = 340;
    private static final int H = 210;
    // Creative inventory tab sprites: 26×32, 27 apart, rising 28 above the panel
    private static final int TAB_W = 26;
    private static final int TAB_H = 32;
    private static final int TAB_STEP = 27;
    private static final int TAB_RISE = 28;
    private static final int ROW_H = 20;
    private static final int SEARCH_ROWS = 6;
    private static final int QUEUE_ROWS = 7;
    private static final int SCROLLBAR_W = 14;
    private static final int SCROLLER_H = 15;
    private static final ResourceLocation SCROLLER = JukeboxMuse.vanillaId("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_DISABLED = JukeboxMuse.vanillaId("container/creative_inventory/scroller_disabled");
    private static final ResourceLocation XP_BACKGROUND = JukeboxMuse.vanillaId("hud/experience_bar_background");
    private static final ResourceLocation XP_PROGRESS = JukeboxMuse.vanillaId("hud/experience_bar_progress");
    private static final ResourceLocation MAP_BACKGROUND = JukeboxMuse.vanillaId("textures/map/map_background.png");
    private static final int XP_W = 182;

    // Now playing
    private static final int COVER_SIZE = 64;
    /** One turn of the record takes this long. */
    private static final float SPIN_MS = 8000f;
    // Lyric box; an untranslated line takes LYRIC_ROW, a translated one LYRIC_ROW + TRANSLATION_ROW
    private static final int LYRIC_TOP = 90;
    private static final int LYRIC_HEIGHT = 80;
    private static final int LYRIC_ROW = 12;
    private static final int TRANSLATION_ROW = 10;
    private static final int LYRIC = 0xFFAAAAAA;
    private static final int LYRIC_CURRENT = 0xFFFFFF55;
    private static final int TRANSLATION = 0xFF707070;
    private static final int TRANSLATION_CURRENT = 0xFFCFCF70;

    // Lists on the search and queue pages
    private static final int SEARCH_LIST_TOP = 44;
    private static final int QUEUE_LIST_TOP = 40;

    // Account page: the QR code is drawn on a map beside a column of controls
    private static final int MAP_SIZE = 170;
    private static final int ACCOUNT_COL = W - MAP_SIZE - 26;
    private static final int LOGGED_IN = 0xFF00AA00;
    private static final int LOGGED_OUT = 0xFFAA0000;

    private static Tab tab = Tab.NOW;
    private static String searchText = "";
    private static int searchScroll;
    private static int queueScroll;
    /** Platform new searches go to, and the platform shown on the account page. */
    private static Platform searchPlatform = Platform.KUGOU;
    private static Platform accountPlatform = Platform.KUGOU;

    private int left;
    private int top;
    private EditBox searchBox;
    private boolean draggingScroller;
    // Snapshot of the state the current widgets were built from; rebuilt when it changes
    private List<Song> builtResults;
    private List<QueueEntry> builtQueue;
    private QueueEntry builtNow;
    private Object builtAccounts;

    public MusicScreen() {
        super(Component.literal("点歌姬"));
        // Nothing to play from yet: take whoever can log in straight to the QR codes
        if (ClientState.canManage && !ClientState.anyLoggedIn()) tab = Tab.ACCOUNT;
    }

    private Tab[] tabs() {
        return ClientState.canManage ? Tab.values() : new Tab[]{Tab.NOW, Tab.SEARCH, Tab.QUEUE};
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        // Centre panel and tabs together
        top = (height - H + TAB_RISE) / 2;
        builtResults = ClientState.searchResults;
        builtQueue = ClientState.queue;
        builtNow = ClientState.nowPlaying;
        builtAccounts = ClientState.accounts;
        searchBox = null;
        if (tab == Tab.ACCOUNT && !ClientState.canManage) tab = Tab.NOW;

        switch (tab) {
            case NOW -> initNow();
            case SEARCH -> initSearch();
            case QUEUE -> initQueue();
            case ACCOUNT -> initAccount();
        }
    }

    // ---------- Now playing ----------

    private void initNow() {
        int y = top + H - 26;
        int x = left + 8;
        addRenderableWidget(Button.builder(Component.literal("切歌"), b -> LoaderServices.get().sendToServer(SkipC2S.INSTANCE))
                .bounds(x, y, 50, 20).build()).active = ClientState.nowPlaying != null;
        addRenderableWidget(Button.builder(Component.literal(ClientSettings.get().muted() ? "取消静音" : "静音"), b -> {
            ClientSettings.get().setMuted(!ClientSettings.get().muted());
            rebuildWidgets();
        }).bounds(x + 54, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal(ClientSettings.get().lyricHud() ? "歌词悬浮：开" : "歌词悬浮：关"), b -> {
            ClientSettings.get().setLyricHud(!ClientSettings.get().lyricHud());
            rebuildWidgets();
        }).bounds(x + 118, y, 84, 20).build());
        addRenderableWidget(new VolumeSlider(x + 206, y, left + W - 8 - (x + 206), 20));
    }

    private void renderNowBackground(GuiGraphics g) {
        if (ClientState.nowPlaying != null) McGui.textBox(g, left + 8, top + LYRIC_TOP, W - 16, LYRIC_HEIGHT + 6);
    }

    private void renderNow(GuiGraphics g) {
        QueueEntry now = ClientState.nowPlaying;
        if (now == null) {
            renderScaledItem(g, Tab.NOW.icon, left + W / 2 - 16, top + 54, 2);
            McGui.centeredLabel(g, font, "现在没有在放歌，去「搜索点歌」点一首吧", left + W / 2, top + 100, McGui.LABEL);
            return;
        }
        Song s = now.song();
        long pos = Math.max(0, ClientState.positionMs());
        int cx = left + 10;
        int cy = top + 20;
        // The record turns with the playback position, so it stops whenever the music does
        CoverArt.renderDisc(g, s, cx, cy, COVER_SIZE, pos % (long) SPIN_MS / SPIN_MS * 360f);

        int tx = cx + COVER_SIZE + 10;
        int tw = left + W - 10 - tx;
        McGui.label(g, font, font.plainSubstrByWidth(s.name(), tw), tx, top + 22, 0xFF202020);
        String detail = s.album().isEmpty() ? s.singer() : s.singer() + " · " + s.album();
        McGui.label(g, font, font.plainSubstrByWidth(detail, tw), tx, top + 34, McGui.LABEL_DIM);
        int fx = tx;
        PlayerInfo info = minecraft == null || minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(now.requester());
        if (info != null) {
            PlayerFaceRenderer.draw(g, info.getSkin(), tx, top + 46, 8);
            fx += 11;
        }
        McGui.label(g, font, font.plainSubstrByWidth(now.requester() + " 点播 · " + s.platform().displayName(), tw - (fx - tx)), fx, top + 46, McGui.LABEL);

        // Progress on the experience bar sprites
        int barY = top + 66;
        long dur = Math.max(1, s.durationMs());
        int filled = (int) (XP_W * Math.min(1.0, pos / (double) dur));
        McGui.sprite(g, XP_BACKGROUND, tx, barY, XP_W, 5);
        if (filled > 0) McGui.spritePart(g, XP_PROGRESS, XP_W, 5, 0, 0, tx, barY, filled, 5);
        McGui.label(g, font, SongRow.time(pos) + " / " + SongRow.time(s.durationMs()), tx + XP_W + 6, barY - 2, McGui.LABEL);

        renderLyrics(g, pos);
    }

    private void renderLyrics(GuiGraphics g, long pos) {
        Lyrics lyrics = ClientState.lyrics;
        int ly = top + LYRIC_TOP + 4;
        int cx = left + W / 2;
        int maxW = W - 28;
        if (lyrics.isEmpty()) {
            g.drawCenteredString(font, "（暂无歌词）", cx, ly + LYRIC_HEIGHT / 2 - 5, LYRIC);
            return;
        }
        List<Lyrics.Line> lines = lyrics.lines();
        int cur = lyrics.indexAt(pos);
        // Grow a window of whole lines around the current one, alternating below and above, until the box is full
        int first = Math.max(0, cur);
        int last = first;
        int used = lyricHeight(lines.get(first));
        boolean grew = true;
        while (grew) {
            grew = false;
            if (last + 1 < lines.size() && used + lyricHeight(lines.get(last + 1)) <= LYRIC_HEIGHT) {
                used += lyricHeight(lines.get(++last));
                grew = true;
            }
            if (first > 0 && used + lyricHeight(lines.get(first - 1)) <= LYRIC_HEIGHT) {
                used += lyricHeight(lines.get(--first));
                grew = true;
            }
        }
        int rowTop = ly;
        for (int idx = first; idx <= last; idx++) {
            Lyrics.Line line = lines.get(idx);
            boolean current = idx == cur;
            g.drawCenteredString(font, font.plainSubstrByWidth(line.text(), maxW), cx, rowTop, current ? LYRIC_CURRENT : LYRIC);
            if (line.translated()) {
                g.drawCenteredString(font, font.plainSubstrByWidth(line.translation(), maxW), cx, rowTop + TRANSLATION_ROW, current ? TRANSLATION_CURRENT : TRANSLATION);
            }
            rowTop += lyricHeight(line);
        }
    }

    private static int lyricHeight(Lyrics.Line line) {
        return line.translated() ? LYRIC_ROW + TRANSLATION_ROW : LYRIC_ROW;
    }

    // ---------- Search ----------

    private void initSearch() {
        if (!ClientState.loggedIn(searchPlatform)) {
            for (Platform p : Platform.values()) {
                if (ClientState.loggedIn(p)) {
                    searchPlatform = p;
                    break;
                }
            }
        }
        // Platform picker: one music disc per platform; platforms without a server account are dimmed
        Platform[] platforms = Platform.values();
        for (int i = 0; i < platforms.length; i++) {
            Platform p = platforms[i];
            boolean usable = ClientState.loggedIn(p);
            Component tip = Component.literal(usable ? p.displayName() : p.displayName() + "（服务器未登录）");
            addRenderableWidget(new IconButton(left + 8 + i * 22, top + 18, 20, 20, McGui.disc(p), tip, p == searchPlatform && usable, usable, () -> {
                searchPlatform = p;
                rebuildWidgets();
            }));
        }
        int boxX = left + 8 + platforms.length * 22 + 2;
        searchBox = new EditBox(font, boxX, top + 19, left + W - 54 - boxX, 18, Component.literal("歌名 / 歌手"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchText);
        searchBox.setResponder(v -> searchText = v);
        searchBox.setHint(Component.literal("在" + searchPlatform.displayName() + "搜索歌名或歌手"));
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);
        addRenderableWidget(Button.builder(Component.literal("搜索"), b -> doSearch(1)).bounds(left + W - 50, top + 18, 42, 20).build());

        List<Song> results = ClientState.searchResults;
        searchScroll = clampScroll(searchScroll, results.size(), SEARCH_ROWS);
        for (int i = 0; i < SEARCH_ROWS && searchScroll + i < results.size(); i++) {
            addRenderableWidget(new SongRow(left + 9, listRowY(SEARCH_LIST_TOP, i), listWidth() - 2, ROW_H, results.get(searchScroll + i)));
        }

        int py = top + H - 26;
        addRenderableWidget(Button.builder(Component.literal("< 上一页"), b -> doSearch(ClientState.searchPage - 1))
                .bounds(left + 8, py, 64, 20).build()).active = ClientState.searchPage > 1 && !ClientState.searchKeyword.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("下一页 >"), b -> doSearch(ClientState.searchPage + 1))
                .bounds(left + W - 72, py, 64, 20).build()).active = !results.isEmpty();
    }

    private void doSearch(int page) {
        String kw = (page == 1 ? searchText : ClientState.searchKeyword).trim();
        if (kw.isEmpty() || page < 1) return;
        // Paging stays on the platform the shown results came from
        Platform platform = page == 1 ? searchPlatform : ClientState.searchPlatform;
        if (!ClientState.loggedIn(platform)) return;
        ClientState.searching = true;
        ClientState.searchError = "";
        searchScroll = 0;
        LoaderServices.get().sendToServer(new SearchC2S(platform, kw, page));
    }

    private void renderSearchBackground(GuiGraphics g) {
        renderList(g, SEARCH_LIST_TOP, SEARCH_ROWS, searchScroll, ClientState.searchResults.size());
    }

    private void renderSearch(GuiGraphics g) {
        int cx = left + 8 + listWidth() / 2;
        int y = listRowY(SEARCH_LIST_TOP, 2) + 6;
        if (!ClientState.anyLoggedIn()) {
            String hint = ClientState.canManage ? "还没有登录任何音乐账号，去「账号」页扫码登录" : "服务器还没有登录任何音乐账号，请联系管理员";
            g.drawCenteredString(font, hint, cx, y, 0xFFFFFFFF);
        } else if (ClientState.searching) {
            g.drawCenteredString(font, "搜索中...", cx, y, 0xFFFFFFFF);
        } else if (!ClientState.searchError.isEmpty()) {
            g.drawCenteredString(font, font.plainSubstrByWidth(ClientState.searchError, listWidth() - 10), cx, y, 0xFFFF5555);
        } else if (ClientState.searchResults.isEmpty()) {
            g.drawCenteredString(font, ClientState.searchKeyword.isEmpty() ? "搜一首歌试试" : "没有搜到结果", cx, y, 0xFFFFFFFF);
        }
        if (!ClientState.searchKeyword.isEmpty()) {
            McGui.centeredLabel(g, font, ClientState.searchPlatform.displayName() + " · 第 " + ClientState.searchPage + " 页", left + W / 2, top + H - 20, McGui.LABEL);
        }
    }

    // ---------- Queue ----------

    private void initQueue() {
        List<QueueEntry> queue = ClientState.queue;
        queueScroll = clampScroll(queueScroll, queue.size(), QUEUE_ROWS);
        String me = minecraft.player == null ? "" : minecraft.player.getScoreboardName();
        for (int i = 0; i < QUEUE_ROWS && queueScroll + i < queue.size(); i++) {
            int index = queueScroll + i;
            QueueEntry e = queue.get(index);
            if (e.requester().equals(me) || ClientState.canManage) {
                Button remove = Button.builder(Component.literal("✕"), b -> {
                    LoaderServices.get().sendToServer(new RemoveC2S(index));
                    b.active = false;
                }).bounds(left + 8 + listWidth() - 19, listRowY(QUEUE_LIST_TOP, i) + 2, 16, 16).build();
                remove.setTooltip(Tooltip.create(Component.literal("从队列删除")));
                addRenderableWidget(remove);
            }
        }
    }

    private void renderQueueBackground(GuiGraphics g) {
        renderList(g, QUEUE_LIST_TOP, QUEUE_ROWS, queueScroll, ClientState.queue.size());
    }

    private void renderQueue(GuiGraphics g) {
        QueueEntry now = ClientState.nowPlaying;
        int x = left + 8;
        if (now != null) g.renderItem(McGui.disc(now.song().platform()), x, top + 19);
        McGui.label(g, font, font.plainSubstrByWidth("正在播放：" + (now == null ? "无" : now.song().title()), W - 40), x + 20, top + 23, McGui.LABEL);

        List<QueueEntry> queue = ClientState.queue;
        if (queue.isEmpty()) {
            g.drawCenteredString(font, "队列是空的", x + listWidth() / 2, listRowY(QUEUE_LIST_TOP, 3) + 6, 0xFFFFFFFF);
            return;
        }
        String me = minecraft.player == null ? "" : minecraft.player.getScoreboardName();
        for (int i = 0; i < QUEUE_ROWS && queueScroll + i < queue.size(); i++) {
            QueueEntry e = queue.get(queueScroll + i);
            int y = listRowY(QUEUE_LIST_TOP, i);
            int rowX = x + 1;
            int rowW = listWidth() - 2;
            if (i % 2 == 1) g.fill(rowX, y, rowX + rowW, y + ROW_H, 0x18FFFFFF);
            g.renderItem(McGui.disc(e.song().platform()), rowX + 2, y + 2);

            // Requester (face + name) on the right, before the delete button
            int nameRight = rowX + rowW - 24;
            String requester = font.plainSubstrByWidth(e.requester(), 70);
            int nameX = nameRight - font.width(requester);
            g.drawString(font, requester, nameX, y + 6, 0xFFE0E0E0);
            PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(e.requester());
            if (info != null) PlayerFaceRenderer.draw(g, info.getSkin(), nameX - 11, y + 6, 8);

            // Your own songs in gold, like a rare item name
            int titleX = rowX + 22;
            String title = font.plainSubstrByWidth((queueScroll + i + 1) + ". " + e.song().title(), nameX - 16 - titleX);
            g.drawString(font, title, titleX, y + 6, e.requester().equals(me) ? 0xFFFFAA00 : 0xFFFFFFFF);
        }
    }

    // ---------- Account ----------

    private void initAccount() {
        int x = left + 8;
        int y = top + 18;
        Platform[] platforms = Platform.values();
        int bw = (ACCOUNT_COL - 2 * (platforms.length - 1)) / platforms.length;
        for (int i = 0; i < platforms.length; i++) {
            Platform p = platforms[i];
            addRenderableWidget(new IconButton(x + i * (bw + 2), y, bw, 20, McGui.disc(p), Component.literal(p.displayName()), p == accountPlatform, true, () -> {
                accountPlatform = p;
                rebuildWidgets();
            }));
        }

        AccountInfo account = ClientState.account(accountPlatform);
        List<LoginMethod> methods = Arrays.stream(LoginMethod.values()).filter(m -> m.platform() == accountPlatform).toList();
        int mw = (ACCOUNT_COL - 2 * (methods.size() - 1)) / methods.size();
        for (int i = 0; i < methods.size(); i++) {
            LoginMethod m = methods.get(i);
            String label = methods.size() == 1 && account.loggedIn() ? "重新扫码登录" : m.buttonLabel();
            addRenderableWidget(Button.builder(Component.literal(label), b -> LoaderServices.get().sendToServer(new LoginStartC2S(m)))
                    .bounds(x + i * (mw + 2), top + 92, mw, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("退出登录"), b -> LoaderServices.get().sendToServer(new LogoutC2S(accountPlatform)))
                .bounds(x, top + 116, ACCOUNT_COL, 20).build()).active = account.loggedIn();
    }

    private void renderAccountBackground(GuiGraphics g) {
        McGui.texture(g, MAP_BACKGROUND, mapX(), top + 20, MAP_SIZE, MAP_SIZE, 64, 64);
    }

    private void renderAccount(GuiGraphics g) {
        int x = left + 8;
        AccountInfo account = ClientState.account(accountPlatform);
        String name = accountPlatform.displayName() + "：";
        String line = account.loggedIn() ? name + account.nickname() + (account.vip() ? "（VIP）" : "") : name + "未登录";
        McGui.label(g, font, font.plainSubstrByWidth(line, ACCOUNT_COL), x, top + 46, account.loggedIn() ? LOGGED_IN : LOGGED_OUT);
        if (!account.loginState().isEmpty()) {
            drawWrapped(g, account.loginState(), x, top + 59, ACCOUNT_COL, McGui.LABEL);
        }
        drawWrapped(g, "建议使用小号：全服共用这个账号搜索和播放，调用过多可能被风控。", x, top + 144, ACCOUNT_COL, McGui.LABEL_DIM);

        // The map's paper starts a few pixels in from its frame
        int margin = MAP_SIZE * 6 / 64;
        int inner = MAP_SIZE - 2 * margin;
        if (!account.qrContent().isEmpty() && QrCode.present()) {
            QrCode.render(g, mapX() + margin, top + 20 + margin, inner);
        } else {
            McGui.centeredLabel(g, font, "选择渠道后点击扫码登录", mapX() + MAP_SIZE / 2, top + 20 + MAP_SIZE / 2 - 4, 0xFF6B5A43);
        }
    }

    private int mapX() {
        return left + W - MAP_SIZE - 10;
    }

    private void drawWrapped(GuiGraphics g, String text, int x, int y, int width, int color) {
        for (var line : font.split(Component.literal(text), width)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
    }

    // ---------- lists ----------

    private int listWidth() {
        return W - 16 - SCROLLBAR_W - 2;
    }

    private int listRowY(int listTop, int i) {
        return top + listTop + 1 + i * ROW_H;
    }

    private static int clampScroll(int scroll, int size, int rows) {
        return Math.max(0, Math.min(scroll, size - rows));
    }

    /** Slot-style list area plus a creative-inventory scrollbar beside it. */
    private void renderList(GuiGraphics g, int listTop, int rows, int scroll, int size) {
        int y = top + listTop;
        int h = rows * ROW_H + 2;
        McGui.inset(g, left + 8, y, listWidth(), h);
        int sx = scrollbarX();
        McGui.inset(g, sx, y, SCROLLBAR_W, h);
        int max = size - rows;
        int sy = y + 1 + (max <= 0 ? 0 : (h - 2 - SCROLLER_H) * scroll / max);
        McGui.sprite(g, max > 0 ? SCROLLER : SCROLLER_DISABLED, sx + 1, sy, 12, SCROLLER_H);
    }

    private int scrollbarX() {
        return left + W - 8 - SCROLLBAR_W;
    }

    /** Which list the scrollbar at the mouse belongs to, as {listTop, rows, size}; null when not over one. */
    private int[] scrollbarAt(double mouseX, double mouseY) {
        int[] list = switch (tab) {
            case SEARCH -> new int[]{SEARCH_LIST_TOP, SEARCH_ROWS, ClientState.searchResults.size()};
            case QUEUE -> new int[]{QUEUE_LIST_TOP, QUEUE_ROWS, ClientState.queue.size()};
            default -> null;
        };
        if (list == null) return null;
        int y = top + list[0];
        int h = list[1] * ROW_H + 2;
        boolean inside = mouseX >= scrollbarX() && mouseX < scrollbarX() + SCROLLBAR_W && mouseY >= y && mouseY < y + h;
        return inside || draggingScroller ? list : null;
    }

    private void scrollTo(double mouseY, int[] list) {
        int max = list[2] - list[1];
        if (max <= 0) return;
        int y = top + list[0];
        int h = list[1] * ROW_H + 2;
        double t = (mouseY - y - 1 - SCROLLER_H / 2.0) / (h - 2 - SCROLLER_H);
        int scroll = (int) Math.round(Math.max(0, Math.min(1, t)) * max);
        setScroll(scroll);
    }

    private void setScroll(int scroll) {
        if (tab == Tab.SEARCH && scroll != searchScroll) {
            searchScroll = clampScroll(scroll, ClientState.searchResults.size(), SEARCH_ROWS);
            rebuildWidgets();
        } else if (tab == Tab.QUEUE && scroll != queueScroll) {
            queueScroll = clampScroll(scroll, ClientState.queue.size(), QUEUE_ROWS);
            rebuildWidgets();
        }
    }

    // ---------- frame ----------

    @Override
    public void tick() {
        super.tick();
        if (builtResults != ClientState.searchResults || builtQueue != ClientState.queue
                || builtNow != ClientState.nowPlaying || builtAccounts != ClientState.accounts) {
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // Like the creative inventory: unselected tabs behind the panel, the selected one in front of it
        Tab[] tabs = tabs();
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != tab) renderTab(g, i, false);
        }
        McGui.panel(g, left, top, W, H);
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == tab) renderTab(g, i, true);
        }
        for (int i = 0; i < tabs.length; i++) {
            g.renderItem(tabs[i].icon, tabX(i) + 5, top - TAB_RISE + 9);
        }
        McGui.label(g, font, tab == Tab.QUEUE ? queueTitle() : tab.title, left + 8, top + 6, McGui.LABEL);

        switch (tab) {
            case NOW -> renderNowBackground(g);
            case SEARCH -> renderSearchBackground(g);
            case QUEUE -> renderQueueBackground(g);
            case ACCOUNT -> renderAccountBackground(g);
        }
    }

    private void renderTab(GuiGraphics g, int i, boolean selected) {
        String sprite = "container/creative_inventory/tab_top_" + (selected ? "selected_" : "unselected_") + (i + 1);
        McGui.sprite(g, JukeboxMuse.vanillaId(sprite), tabX(i), top - TAB_RISE, TAB_W, TAB_H);
    }

    private int tabX(int i) {
        return left + i * TAB_STEP;
    }

    private String queueTitle() {
        List<QueueEntry> queue = ClientState.queue;
        if (queue.isEmpty()) return Tab.QUEUE.title;
        long ms = queue.stream().mapToLong(e -> e.song().durationMs()).sum();
        return Tab.QUEUE.title + " · 共 " + queue.size() + " 首，约 " + Math.max(1, (ms + 59_999) / 60_000) + " 分钟";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        switch (tab) {
            case NOW -> renderNow(g);
            case SEARCH -> renderSearch(g);
            case QUEUE -> renderQueue(g);
            case ACCOUNT -> renderAccount(g);
        }
        Tab[] tabs = tabs();
        for (int i = 0; i < tabs.length; i++) {
            if (overTab(i, mouseX, mouseY)) {
                //? if >=1.21.6 {
                /*g.setTooltipForNextFrame(font, Component.literal(tabs[i].title), mouseX, mouseY);
                *///?} else
                g.renderTooltip(font, Component.literal(tabs[i].title), mouseX, mouseY);
            }
        }
    }

    private boolean overTab(int i, double mouseX, double mouseY) {
        return mouseX >= tabX(i) && mouseX < tabX(i) + TAB_W && mouseY >= top - TAB_RISE && mouseY < top;
    }

    // Input. From 1.21.9 Screen takes event records; the overrides unpack them for these handlers.

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return clicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        return dragged(event.x(), event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingScroller = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return pressed(event.key(), ClientHooks.OPEN_KEY.matches(event)) || super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return dragged(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroller = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return pressed(keyCode, ClientHooks.OPEN_KEY.matches(keyCode, scanCode)) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    /** Tabs and the scrollbars; true when the click was used. */
    private boolean clicked(double mouseX, double mouseY, int button) {
        Tab[] tabs = tabs();
        for (int i = 0; i < tabs.length; i++) {
            if (button == 0 && overTab(i, mouseX, mouseY)) {
                if (tabs[i] != tab) {
                    tab = tabs[i];
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    rebuildWidgets();
                }
                return true;
            }
        }
        int[] list = scrollbarAt(mouseX, mouseY);
        if (button == 0 && list != null) {
            draggingScroller = true;
            scrollTo(mouseY, list);
            return true;
        }
        return false;
    }

    /** Dragging a scrollbar; true when the drag was used. */
    private boolean dragged(double mouseX, double mouseY) {
        if (!draggingScroller) return false;
        int[] list = scrollbarAt(mouseX, mouseY);
        if (list != null) scrollTo(mouseY, list);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (delta != 0 && tab == Tab.SEARCH) {
            setScroll(searchScroll + delta);
            return true;
        }
        if (delta != 0 && tab == Tab.QUEUE) {
            setScroll(queueScroll + delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Enter to search, and the open-screen key to close; true when the key was used. */
    private boolean pressed(int keyCode, boolean openKey) {
        boolean typing = tab == Tab.SEARCH && searchBox != null && searchBox.isFocused();
        if (typing && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            doSearch(1);
            return true;
        }
        // Don't let the open-screen key close the screen while typing
        if (typing && keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        if (openKey) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void renderScaledItem(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        McGui.push(g);
        McGui.translate(g, x, y);
        McGui.scale(g, scale);
        g.renderItem(stack, 0, 0);
        McGui.pop(g);
    }

    private static final class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), ClientSettings.get().volume());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("音量 " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            ClientSettings.get().setVolume(value);
        }
    }
}
