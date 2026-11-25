package pl.szybki.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;

public class RadarScreen extends Screen {
    static final int LW = 380, RW = 268, GAP = 6, TOT = LW + GAP + RW, H = 336;
    static final int[] STEPS = {1, 2, 4, 8};   // bloków na komórkę mapy
    static final int CELL = 3;                  // piksele na komórkę
    static final int BLUE = 0x3B82F6;
    static final String[] TABS = {"mapa", "gracze", "opcje"};

    private record Hit(String name, int x, int y, int w, int h) {}

    // układ
    private float s = 1f;
    private int ox, oy;
    private long openAt;

    // stan
    private int tab = 0;
    private float tabPos = 0;
    private int mode = 0;          // 0 = set, 1 = kostium, 2 = pet
    private int drag = 0;          // suwaki: 1 interwał, 2 zasięg, 3 min. dystans
    private boolean mapDrag = false;
    private double panX = 0, panZ = 0;
    private int scroll = 0;
    private String lastClickName = null;
    private long lastClickTime = 0;
    private int pill1W = 90, pill2W = 90;

    // mapa
    private int[] mapCols = new int[0];
    private int mapColsN = 0, mapRowsN = 0, mapStep = 2;
    private double mapCX, mapCZ;
    private long mapBuilt = 0;

    private final List<Hit> hits = new ArrayList<>();
    private ItemStack hoverStack = null;

    public RadarScreen() {
        super(Text.literal("Fuzioll Radar"));
    }

    @Override protected void init() { openAt = System.currentTimeMillis(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mx, int my, float d) { }
    @Override public void removed() { RadarConfig.save(); }

    // ================= pomocnicze =================
    static int col(int rgb, float a) {
        int al = Math.max(5, Math.min(255, (int) (a * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    static int mix(int a, int b, float k) {
        k = Math.max(0, Math.min(1, k));
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void layout() {
        s = Math.min(1f, Math.min((width - 6f) / TOT, (height - 6f) / H));
        ox = (int) ((width / s - TOT) / 2);
        oy = (int) ((height / s - H) / 2);
    }

    private void t(DrawContext c, String str, int x, int y, int color) { Fx.draw(c, textRenderer, str, x, y, color); }
    private void l(DrawContext c, String str, int x, int y, int color) { Fx.draw(c, textRenderer, Fx.sc(str), x, y, color); }
    private int lw(String str) { return Fx.w(textRenderer, Fx.sc(str)); }
    private int tw(String str) { return Fx.w(textRenderer, str); }

    private AbstractClientPlayerEntity findPlayer(String name) {
        if (name == null || client == null || client.world == null) return null;
        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (p.getGameProfile().getName().equals(name)) return p;
        }
        return null;
    }

    private void face(DrawContext c, String name, int x, int y, int size) {
        AbstractClientPlayerEntity p = findPlayer(name);
        try {
            if (p != null) {
                PlayerSkinDrawer.draw(c, p.getSkinTextures(), x, y, size);
            } else if (client.getNetworkHandler() != null) {
                PlayerListEntry e = client.getNetworkHandler().getPlayerListEntry(name);
                if (e != null) PlayerSkinDrawer.draw(c, e.getSkinTextures(), x, y, size);
            }
        } catch (Exception ignored) {
        }
    }

    private void selectOrLock(String name) {
        long now = System.currentTimeMillis();
        if (name.equals(lastClickName) && now - lastClickTime < 450) {
            Radar.target = name;            // 2 kliki = cel TPA
            Radar.selected = name;
            lastClickName = null;
        } else {
            if (!name.equals(Radar.selected)) mode = 0;
            Radar.selected = name;          // 1 klik = podgląd
            lastClickName = name;
            lastClickTime = now;
        }
    }

    private static String fmt(float v) { return GearInfo.fmt(v); }

    // ================= render =================
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        layout();
        hits.clear();
        hoverStack = null;
        long now = System.currentTimeMillis();
        float f = 1f;
        if (Theme.anim) {
            f = Math.min(1f, (now - openAt) / 300f);
            f = 1 - (1 - f) * (1 - f) * (1 - f);
        }
        float time = (now % 600000L) / 1000f;
        float vmx = mx / s, vmy = my / s;
        int px = ox, py = oy + (Theme.anim ? (int) ((1 - f) * 16) : 0);

        c.fill(0, 0, width, height, col(Theme.dim(), 0.55f * f));
        drawPills(c, mx, my, f);

        c.getMatrices().push();
        c.getMatrices().scale(s, s, 1f);

        // ---------- lewy panel ----------
        panel(c, px, py, LW, H, f, time);
        header(c, px, py, f, time);

        // zakładki
        for (int i = 0; i < TABS.length; i++) {
            int tx = px + 10 + i * 92;
            boolean hov = in(vmx, vmy, tx, py + 44, 88, 18);
            if (hov) c.fill(tx, py + 44, tx + 88, py + 62, col(Theme.panel2(), f));
            l(c, TABS[i], tx + (88 - lw(TABS[i])) / 2, py + 49, col(tab == i ? Theme.a1() : Theme.sub(), f));
        }
        tabPos += (tab - tabPos) * (Theme.anim ? 0.25f : 1f);
        int ux = px + 10 + (int) (tabPos * 92);
        c.fill(ux, py + 61, ux + 88, py + 63, col(Theme.anim ? mix(Theme.a1(), Theme.a2(), (float) (Math.sin(time * 2) * 0.5 + 0.5)) : Theme.a1(), f));
        c.fill(px + 10, py + 63, px + LW - 10, py + 64, col(Theme.line(), f));

        int cx0 = px + 10, cy0 = py + 68, cw = LW - 20, ch = 224;
        if (tab == 0) renderMap(c, cx0, cy0, cw, ch, vmx, vmy, f, time);
        else if (tab == 1) renderPlayers(c, cx0, cy0, cw, ch, vmx, vmy, f);
        else renderOptions(c, cx0, cy0, cw, vmx, vmy, f);

        renderTpa(c, px + 10, py + 298, LW - 20, vmx, vmy, f);

        // ---------- prawy panel ----------
        int rx = px + LW + GAP;
        panel(c, rx, py, RW, H, f, time + 1.5f);
        renderDetail(c, rx, py, vmx, vmy, f, time, mx, my);

        c.getMatrices().pop();

        if (hoverStack != null && !hoverStack.isEmpty()) {
            c.drawItemTooltip(textRenderer, hoverStack, mx, my);
        }
    }

    private void panel(DrawContext c, int x, int y, int w, int h, float f, float time) {
        int border = Theme.anim ? mix(Theme.a1(), Theme.a2(), (float) (Math.sin(time) * 0.5 + 0.5)) : Theme.line();
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, col(border, (Theme.anim ? 0.75f : 1f) * f));
        c.fill(x, y, x + w, y + h, col(Theme.panel(), 0.98f * f));
    }

    private void header(DrawContext c, int px, int py, float f, float time) {
        c.fillGradient(px, py, px + LW, py + 40, col(Theme.panel2(), f), col(Theme.panel(), f));
        c.getMatrices().push();
        c.getMatrices().translate(px + 14, py + 11, 0);
        c.getMatrices().scale(1.5f, 1.5f, 1f);
        String title = "SZYBKI RADAR";
        int tx = 0;
        for (int i = 0; i < title.length(); i++) {
            String ch = title.substring(i, i + 1);
            int cc = Theme.anim
                    ? mix(Theme.a1(), Theme.a2(), (float) (Math.sin(time * 2.5 - i * 0.5) * 0.5 + 0.5))
                    : Theme.a1();
            Fx.draw(c, textRenderer, ch, tx, 0, col(cc, f));
            tx += tw(ch);
        }
        c.getMatrices().pop();

        float pulse = Theme.anim ? (float) (Math.sin(time * 4) * 0.5 + 0.5) : 1f;
        String st = Radar.repeat ? "● powtarza tpa" : "● gotowy";
        l(c, st, px + LW - 14 - lw(st), py + 10, col(Radar.repeat ? Theme.good() : Theme.sub(), 0.6f + 0.4f * pulse));
        String ver = "szybki client • mc 1.21.4";
        l(c, ver, px + LW - 14 - lw(ver), py + 24, col(Theme.sub(), f));
    }

    private void drawPills(DrawContext c, int mx, int my, float f) {
        String a = "◐ motyw: " + (Theme.dark ? "ciemny" : "jasny");
        String b = "✦ styl: " + (Theme.anim ? "animowany" : "matowy");
        pill1W = lw(a) + 14;
        pill2W = lw(b) + 14;
        pill(c, 6, 6, pill1W, a, in(mx, my, 6, 6, pill1W, 14), f);
        pill(c, 6 + pill1W + 4, 6, pill2W, b, in(mx, my, 6 + pill1W + 4, 6, pill2W, 14), f);
    }

    private void pill(DrawContext c, int x, int y, int w, String label, boolean hov, float f) {
        c.fill(x - 1, y - 1, x + w + 1, y + 15, col(Theme.a1(), 0.8f));
        c.fill(x, y, x + w, y + 14, col(hov ? Theme.panel2() : Theme.panel(), 0.97f));
        l(c, label, x + 7, y + 3, col(Theme.text(), 1f));
    }

    // ================= MAPA =================
    private void ensureMap(long now, double cxw, double czw, int step, int cols, int rows) {
        ClientWorld w = client.world;
        boolean need = mapCols.length != cols * rows || mapStep != step
                || Math.abs(cxw - mapCX) > step * 0.75 || Math.abs(czw - mapCZ) > step * 0.75
                || now - mapBuilt > 700;
        if (!need) return;
        mapCX = cxw;
        mapCZ = czw;
        mapStep = step;
        mapBuilt = now;
        mapColsN = cols;
        mapRowsN = rows;
        mapCols = new int[cols * rows];
        int[] hgt = new int[cols * rows];
        boolean ceil = w.getDimension().hasCeiling();
        int pY = client.player.getBlockY();
        int bottom = w.getBottomY();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int r = 0; r < rows; r++) {
            for (int cc = 0; cc < cols; cc++) {
                int idx = r * cols + cc;
                int wx = (int) Math.floor(cxw + (cc - cols / 2.0 + 0.5) * step);
                int wz = (int) Math.floor(czw + (r - rows / 2.0 + 0.5) * step);
                if (w.getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false) == null) continue;
                int top = w.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz) - 1;
                int y = ceil ? Math.min(pY + 6, top) : top;
                int color = -1;
                for (int tries = 0; tries < 24 && y > bottom; tries++, y--) {
                    pos.set(wx, y, wz);
                    BlockState st = w.getBlockState(pos);
                    if (st.isAir()) continue;
                    MapColor mc = st.getMapColor(w, pos);
                    if (mc == MapColor.CLEAR) continue;
                    color = mc.color;
                    break;
                }
                if (color < 0) continue;
                hgt[idx] = y;
                mapCols[idx] = 0xFF000000 | color;
            }
        }
        // cieniowanie reliefu
        for (int r = 1; r < rows; r++) {
            for (int cc = 1; cc < cols; cc++) {
                int idx = r * cols + cc;
                if (mapCols[idx] == 0 || mapCols[idx - cols] == 0) continue;
                int dh = hgt[idx] - hgt[idx - cols];
                float k = dh > 0 ? 1.15f : (dh < 0 ? 0.82f : 1f);
                if (k != 1f) {
                    int cr = Math.min(255, (int) (((mapCols[idx] >> 16) & 255) * k));
                    int cg = Math.min(255, (int) (((mapCols[idx] >> 8) & 255) * k));
                    int cb = Math.min(255, (int) ((mapCols[idx] & 255) * k));
                    mapCols[idx] = 0xFF000000 | (cr << 16) | (cg << 8) | cb;
                }
            }
        }
    }

    private int[] w2s(double wx, double wz, int mx0, int my0) {
        double bpp = mapStep / (double) CELL;
        int sx = (int) Math.round(mx0 + mapColsN * CELL / 2.0 + (wx - mapCX) / bpp);
        int sy = (int) Math.round(my0 + mapRowsN * CELL / 2.0 + (wz - mapCZ) / bpp);
        return new int[]{sx, sy};
    }

    private void renderMap(DrawContext c, int x0, int y0, int w, int h, float vmx, float vmy, float f, float time) {
        if (client.world == null || client.player == null) return;
        int step = STEPS[Math.max(0, Math.min(STEPS.length - 1, Radar.zoom))];
        int cols = w / CELL, rows = h / CELL;
        double cxw = client.player.getX() + panX, czw = client.player.getZ() + panZ;
        ensureMap(System.currentTimeMillis(), cxw, czw, step, cols, rows);
        double bpp = mapStep / (double) CELL;
        int mw = mapColsN * CELL, mh = mapRowsN * CELL;

        c.fill(x0 - 1, y0 - 1, x0 + mw + 1, y0 + mh + 1, col(Theme.line(), f));
        c.fill(x0, y0, x0 + mw, y0 + mh, col(Theme.mapBg(), f));

        // teren (łączenie sąsiednich komórek o tym samym kolorze)
        for (int r = 0; r < mapRowsN; r++) {
            int runStart = 0, runCol = mapCols[r * mapColsN];
            for (int cc = 1; cc <= mapColsN; cc++) {
                int cur = cc < mapColsN ? mapCols[r * mapColsN + cc] : Integer.MIN_VALUE;
                if (cur != runCol) {
                    if (runCol != 0) {
                        c.fill(x0 + runStart * CELL, y0 + r * CELL, x0 + cc * CELL, y0 + (r + 1) * CELL,
                                (runCol & 0xFFFFFF) | ((int) (255 * f) << 24));
                    }
                    runStart = cc;
                    runCol = cur;
                }
            }
        }
        // lekkie przyciemnienie dla czytelności znaczników
        c.fill(x0, y0, x0 + mw, y0 + mh, col(0x000000, Theme.dark ? 0.18f : 0.04f));

        int[] me = w2s(client.player.getX(), client.player.getZ(), x0, y0);

        // pierścienie zasięgu co 50 m
        for (int rng = 50; rng <= Radar.range; rng += 50) {
            int rr = (int) (rng / bpp);
            if (rr < 6 || rr > 700) continue;
            ring(c, me[0], me[1], rr, col(Theme.a1(), 0.45f * f), x0, y0, mw, mh);
            if (me[1] - rr > y0 + 2 && me[1] - rr < y0 + mh - 10) {
                t(c, rng + "m", me[0] + 3, me[1] - rr + 2, col(Theme.a1(), f));
            }
        }
        // animowany sweep i ping
        if (Theme.anim) {
            int maxR = (int) Math.min(Radar.range / bpp, 400);
            float ping = (time % 2.5f) / 2.5f;
            ring(c, me[0], me[1], (int) (ping * maxR), col(Theme.a1(), (1 - ping) * 0.8f * f), x0, y0, mw, mh);
            for (int k = 0; k < 10; k++) {
                double ang = time * 2.0 - k * 0.08;
                int ex = me[0] + (int) (Math.cos(ang) * maxR), ey = me[1] + (int) (Math.sin(ang) * maxR);
                float a = (1 - k / 10f) * 0.5f;
                sweepLine(c, me[0], me[1], ex, ey, col(Theme.a1(), a * f), x0, y0, mw, mh);
            }
        }

        // gracze: skórki jako znaczniki, nicki bez nakładania
        List<int[]> used = new ArrayList<>();
        List<String[]> pending = new ArrayList<>();
        String selName = Radar.selected, tgtName = Radar.target;
        List<Radar.Target> ordered = new ArrayList<>();
        for (Radar.Target tg : Radar.targets) if (tg.dist() >= 0) ordered.add(tg);

        String hovered = null;
        List<int[]> pos = new ArrayList<>();
        for (Radar.Target tg : ordered) {
            int[] p = w2s(client.player.getX() + tg.dx(), client.player.getZ() + tg.dz(), x0, y0);
            pos.add(p);
            boolean special = tg.name().equals(selName) || tg.name().equals(tgtName);
            boolean inside = p[0] >= x0 + 5 && p[0] < x0 + mw - 5 && p[1] >= y0 + 5 && p[1] < y0 + mh - 5;
            if (!inside) {
                if (!special) continue;
                p[0] = Math.max(x0 + 6, Math.min(x0 + mw - 6, p[0]));
                p[1] = Math.max(y0 + 6, Math.min(y0 + mh - 6, p[1]));
            }
            if (in(vmx, vmy, p[0] - 6, p[1] - 6, 12, 12)) hovered = tg.name();
        }

        if (selName != null) {
            for (int i = 0; i < ordered.size(); i++) {
                if (ordered.get(i).name().equals(selName)) {
                    int[] p = pos.get(i);
                    int ex = Math.max(x0, Math.min(x0 + mw, p[0])), ey = Math.max(y0, Math.min(y0 + mh, p[1]));
                    Fx.line(c, me[0], me[1], ex, ey, col(BLUE, 0.75f * f), 2);
                }
            }
        }

        for (int i = 0; i < ordered.size(); i++) {
            Radar.Target tg = ordered.get(i);
            int[] p = pos.get(i);
            boolean isSel = tg.name().equals(selName), isTgt = tg.name().equals(tgtName);
            boolean inside = p[0] >= x0 + 5 && p[0] < x0 + mw - 5 && p[1] >= y0 + 5 && p[1] < y0 + mh - 5;
            if (!inside) {
                if (!(isSel || isTgt)) continue;
                p[0] = Math.max(x0 + 6, Math.min(x0 + mw - 6, p[0]));
                p[1] = Math.max(y0 + 6, Math.min(y0 + mh - 6, p[1]));
            }
            if (isSel) {
                int rr = 8 + (Theme.anim ? (int) (Math.sin(time * 5) * 2 + 2) : 2);
                c.fill(p[0] - rr, p[1] - rr, p[0] + rr + 1, p[1] + rr + 1, col(BLUE, 0.35f * f));
                c.fill(p[0] - 7, p[1] - 7, p[0] + 8, p[1] + 8, col(BLUE, f));
            } else if (isTgt) {
                c.fill(p[0] - 7, p[1] - 7, p[0] + 8, p[1] + 8, col(Theme.good(), f));
            } else {
                c.fill(p[0] - 6, p[1] - 6, p[0] + 7, p[1] + 7, col(0x000000, 0.7f * f));
            }
            face(c, tg.name(), p[0] - 5, p[1] - 5, 10);
            hits.add(new Hit(tg.name(), p[0] - 7, p[1] - 7, 15, 15));
            boolean showLabel = Radar.nicks || isSel || isTgt || tg.name().equals(hovered);
            if (showLabel) pending.add(new String[]{tg.name(), String.valueOf(i), isSel ? "0" : (isTgt ? "1" : (tg.name().equals(hovered) ? "2" : "3"))});
        }
        pending.sort((a, b) -> a[2].compareTo(b[2]));
        for (String[] pd : pending) {
            int[] p = pos.get(Integer.parseInt(pd[1]));
            String nm = pd[0];
            int lw2 = tw(nm) + 6;
            int[][] tries = {{9, -5}, {-9 - lw2, -5}, {-lw2 / 2, -20}, {-lw2 / 2, 10}};
            for (int[] tr : tries) {
                int lx = p[0] + tr[0], ly = p[1] + tr[1];
                if (lx < x0 || lx + lw2 > x0 + mw || ly < y0 || ly + 11 > y0 + mh) continue;
                boolean clash = false;
                for (int[] u : used) {
                    if (lx < u[0] + u[2] && lx + lw2 > u[0] && ly < u[1] + u[3] && ly + 11 > u[1]) { clash = true; break; }
                }
                if (clash && !pd[2].equals("0") && !pd[2].equals("1")) continue;
                used.add(new int[]{lx, ly, lw2, 11});
                c.fill(lx, ly, lx + lw2, ly + 11, col(Theme.panel(), 0.82f * f));
                if (pd[2].equals("0")) c.fill(lx, ly, lx + lw2, ly + 1, col(BLUE, f));
                t(c, nm, lx + 3, ly + 2, col(pd[2].equals("0") ? BLUE : (Theme.dark ? 0xFFFFFF : 0x141A26), f));
                break;
            }
        }

        // Ty
        c.fill(me[0] - 3, me[1] - 3, me[0] + 4, me[1] + 4, col(0xFFFFFF, f));
        c.fill(me[0] - 2, me[1] - 2, me[0] + 3, me[1] + 3, col(Theme.a1(), f));
        double yawR = Math.toRadians(client.player.getYaw());
        Fx.line(c, me[0], me[1], me[0] + (int) (-Math.sin(yawR) * 10), me[1] + (int) (Math.cos(yawR) * 10), col(0xFFFFFF, f), 2);

        // kompas
        t(c, "N", x0 + mw / 2 - 3, y0 + 3, col(Theme.warn(), f));
        // skala
        int len = (int) (50 / bpp);
        c.fill(x0 + 6, y0 + mh - 10, x0 + 6 + len, y0 + mh - 8, col(Theme.text(), f));
        t(c, "50m", x0 + 10 + len, y0 + mh - 14, col(Theme.text(), f));
        l(c, "zoom: " + (1 + STEPS.length - 1 - Radar.zoom) + "/" + STEPS.length + "  •  kółko = zoom, przeciągnij = przesuń",
                x0 + 6, y0 + 4 + 12, col(Theme.sub(), 0.9f * f));

        // przyciski mapy
        mapBtn(c, x0 + mw - 22, y0 + 4, "+", vmx, vmy, f);
        mapBtn(c, x0 + mw - 22, y0 + 24, "-", vmx, vmy, f);
        mapBtn(c, x0 + mw - 22, y0 + 44, "◎", vmx, vmy, f);
    }

    private void mapBtn(DrawContext c, int x, int y, String label, float vmx, float vmy, float f) {
        boolean hov = in(vmx, vmy, x, y, 18, 18);
        c.fill(x - 1, y - 1, x + 19, y + 19, col(Theme.a1(), 0.8f * f));
        c.fill(x, y, x + 18, y + 18, col(hov ? Theme.panel2() : Theme.panel(), 0.95f * f));
        t(c, label, x + 9 - tw(label) / 2, y + 5, col(Theme.text(), f));
    }

    private void ring(DrawContext c, int cx, int cy, int r, int color, int bx, int by, int bw, int bh) {
        if (r < 2) return;
        double stepA = Math.max(0.02, 1.2 / r);
        for (double a = 0; a < Math.PI * 2; a += stepA) {
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            if (x < bx || y < by || x >= bx + bw || y >= by + bh) continue;
            c.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void sweepLine(DrawContext c, int x0, int y0, int x1, int y1, int color, int bx, int by, int bw, int bh) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 2; i <= steps; i += 2) {
            int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            if (x < bx || y < by || x >= bx + bw || y >= by + bh) continue;
            c.fill(x, y, x + 1, y + 1, color);
        }
    }

    // ================= GRACZE =================
    private void renderPlayers(DrawContext c, int x0, int y0, int w, int h, float vmx, float vmy, float f) {
        int total = Radar.targets.size();
        l(c, "wykryto (" + total + ")  •  1 klik = podgląd  •  2 kliki = cel tpa", x0, y0 + 2, col(Theme.a1(), f));
        int rows = (h - 18) / 16;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - rows)));
        for (int i = 0; i < rows && scroll + i < total; i++) {
            Radar.Target tg = Radar.targets.get(scroll + i);
            int ry = y0 + 16 + i * 16;
            boolean sel = tg.name().equals(Radar.selected), tgt = tg.name().equals(Radar.target);
            boolean hov = in(vmx, vmy, x0, ry, w, 15);
            if (sel || hov) c.fill(x0, ry, x0 + w, ry + 15, col(sel ? BLUE : Theme.panel2(), sel ? 0.28f * f : f));
            if (sel) c.fill(x0, ry, x0 + 2, ry + 15, col(BLUE, f));
            face(c, tg.name(), x0 + 6, ry + 2, 11);
            t(c, tg.name(), x0 + 24, ry + 4, col(sel ? BLUE : Theme.text(), f));
            String d = tg.dist() < 0 ? "serwer" : String.format("%.0fm", tg.dist());
            t(c, d, x0 + w - tw(d) - 6, ry + 4, col(Theme.sub(), f));
            if (tgt) l(c, "cel", x0 + w - tw(d) - 14 - lw("cel"), ry + 4, col(Theme.good(), f));
            hits.add(new Hit(tg.name(), x0, ry, w, 15));
        }
        if (total == 0) l(c, "brak graczy w zasięgu", x0 + 6, y0 + 24, col(Theme.sub(), f));
    }

    // ================= OPCJE =================
    private void renderOptions(DrawContext c, int x0, int y0, int w, float vmx, float vmy, float f) {
        boolean hov = in(vmx, vmy, x0, y0 + 4, w, 22);
        c.fill(x0, y0 + 4, x0 + w, y0 + 26, col(hov ? Theme.panel2() : mix(Theme.panel2(), Theme.panel(), 0.5f), f));
        c.fill(x0, y0 + 4, x0 + 2, y0 + 26, col(Theme.a1(), f));
        l(c, "lista graczy: " + (Radar.serverWide ? "cały serwer (tab)" : "tylko w pobliżu"), x0 + 10, y0 + 11, col(Theme.text(), f));

        chip(c, x0, y0 + 32, 176, "niebieskie podświetlenie: " + (Radar.esp ? "tak" : "nie"), Radar.esp, vmx, vmy, f);
        chip(c, x0 + 184, y0 + 32, 176, "nicki na mapie: " + (Radar.nicks ? "tak" : "nie"), Radar.nicks, vmx, vmy, f);

        slider(c, x0, y0 + 60, w, "interwał powtarzania tpa", String.format("%.1fs", Radar.intervalSec), (Radar.intervalSec - 1f) / 9f, f);
        slider(c, x0, y0 + 92, w, "zasięg radaru", Radar.range + "m", (Radar.range - 16) / 184f, f);
        slider(c, x0, y0 + 124, w, "min. dystans (bliżej = nie wysyła tpa)", Radar.minDist + "m", Radar.minDist / 20f, f);

        l(c, "jak używać:", x0, y0 + 158, col(Theme.a1(), f));
        l(c, "1 klik na graczu = podgląd + niebieskie podświetlenie", x0, y0 + 171, col(Theme.sub(), f));
        l(c, "2 kliki = wybierasz go jako cel tpa", x0, y0 + 183, col(Theme.sub(), f));
        l(c, "potem zielony przycisk „tepnij do …” na dole", x0, y0 + 195, col(Theme.sub(), f));
        l(c, "r = włącz/wyłącz powtarzanie  •  prawy klik = odznacz", x0, y0 + 207, col(Theme.sub(), f));
    }

    private void chip(DrawContext c, int x, int y, int w, String label, boolean on, float vmx, float vmy, float f) {
        boolean hov = in(vmx, vmy, x, y, w, 18);
        c.fill(x, y, x + w, y + 18, col(hov ? Theme.panel2() : mix(Theme.panel2(), Theme.panel(), 0.5f), f));
        c.fill(x, y, x + 2, y + 18, col(on ? Theme.good() : Theme.sub(), f));
        l(c, label, x + 8, y + 5, col(on ? Theme.text() : Theme.sub(), f));
    }

    private void slider(DrawContext c, int x, int y, int w, String label, String value, float k, float f) {
        k = Math.max(0, Math.min(1, k));
        l(c, label, x, y, col(Theme.sub(), f));
        l(c, value, x + w - lw(value), y, col(Theme.a1(), f));
        c.fill(x, y + 14, x + w, y + 18, col(Theme.panel2(), f));
        int fw = (int) (w * k);
        for (int i = 0; i < fw; i++) {
            c.fill(x + i, y + 14, x + i + 1, y + 18, col(mix(Theme.a1(), Theme.a2(), i / (float) w), f));
        }
        c.fill(x + fw - 2, y + 10, x + fw + 3, y + 22, col(Theme.text(), f));
    }

    // ================= TPA =================
    private void renderTpa(DrawContext c, int x, int y, int w, float vmx, float vmy, float f) {
        c.fill(x, y, x + w, y + 30, col(Theme.panel2(), f));
        c.fill(x, y, x + 2, y + 30, col(Theme.good(), f));
        String tg = Radar.target;
        l(c, "cel tpa:", x + 8, y + 4, col(Theme.sub(), f));
        t(c, tg != null ? Fx.fit(textRenderer, tg, 120) : "— kliknij gracza 2 razy", x + 8 + lw("cel tpa:") + 5, y + 4,
                col(tg != null ? Theme.good() : Theme.sub(), f));
        l(c, "ostatnio: " + Radar.lastSent, x + 8, y + 17, col(Theme.sub(), f));

        // powtarzanie
        int rx = x + w - 150 - 8 - 110;
        boolean hovR = in(vmx, vmy, rx, y + 5, 110, 20);
        c.fill(rx, y + 5, rx + 110, y + 25, col(hovR ? Theme.panel() : mix(Theme.panel2(), Theme.panel(), 0.4f), f));
        c.fill(rx, y + 5, rx + 2, y + 25, col(Radar.repeat ? Theme.good() : Theme.sub(), f));
        String rep = "powtarzaj: " + (Radar.repeat ? "tak" : "nie");
        l(c, rep, rx + 9, y + 11, col(Radar.repeat ? Theme.text() : Theme.sub(), f));

        // zielony przycisk
        int bx = x + w - 150 - 4;
        boolean hov = in(vmx, vmy, bx, y + 4, 150, 22) && tg != null;
        int base = tg != null ? (hov ? 0x22C55E : 0x16A34A) : 0x4B5563;
        c.fill(bx - 1, y + 3, bx + 151, y + 27, col(tg != null ? 0x4ADE80 : 0x6B7280, (hov ? 0.95f : 0.6f) * f));
        c.fill(bx, y + 4, bx + 150, y + 26, col(base, f));
        String lbl = tg != null ? "tepnij do " + Fx.fit(textRenderer, tg, 90) : "tepnij do …";
        l(c, lbl, bx + (150 - lw(lbl)) / 2, y + 11, col(0xFFFFFF, f));
    }

    // ================= SZCZEGÓŁY GRACZA =================
    private void button(DrawContext c, int x, int y, int w, String label, boolean active, float vmx, float vmy, float f) {
        boolean hov = in(vmx, vmy, x, y, w, 15);
        int base = active ? 0x22C55E : 0x15803D;
        c.fill(x - 1, y - 1, x + w + 1, y + 16, col(0x4ADE80, (hov ? 0.95f : 0.55f) * f));
        c.fill(x, y, x + w, y + 15, col(hov ? mix(base, 0xFFFFFF, 0.12f) : base, f));
        l(c, label, x + (w - lw(label)) / 2, y + 4, col(0xFFFFFF, f));
    }

    private void renderDetail(DrawContext c, int rx, int py, float vmx, float vmy, float f, float time, int mx, int my) {
        String sel = Radar.selected;
        AbstractClientPlayerEntity p = findPlayer(sel);
        if (sel == null) {
            l(c, "skaner gracza", rx + 12, py + 14, col(Theme.a1(), f));
            l(c, "kliknij gracza na mapie lub", rx + 12, py + 36, col(Theme.sub(), f));
            l(c, "na liście, żeby zobaczyć:", rx + 12, py + 48, col(Theme.sub(), f));
            l(c, "• model 3d, hp, set / kostium", rx + 12, py + 66, col(Theme.sub(), f));
            l(c, "• peta serwerowego", rx + 12, py + 78, col(Theme.sub(), f));
            l(c, "• zbroję i przedmioty w rękach", rx + 12, py + 90, col(Theme.sub(), f));
            l(c, "• ostatnio trzymane (pasek)", rx + 12, py + 102, col(Theme.sub(), f));
            return;
        }
        if (p == null) {
            t(c, sel, rx + 12, py + 14, col(BLUE, f));
            l(c, "poza zasięgiem – serwer nie", rx + 12, py + 36, col(Theme.sub(), f));
            l(c, "wysyła jego ekwipunku.", rx + 12, py + 48, col(Theme.sub(), f));
            return;
        }

        GearInfo.Info info = GearInfo.of(p, client.world);

        // model 3D
        int mx0 = rx + 10, my0 = py + 10, mw = 72, mh = 98;
        c.fill(mx0 - 1, my0 - 1, mx0 + mw + 1, my0 + mh + 1, col(Theme.line(), f));
        c.fill(mx0, my0, mx0 + mw, my0 + mh, col(Theme.mapBg(), f));
        Entity shown = mode == 2 ? info.pet() : p;
        if (shown instanceof LivingEntity le) {
            int sz = (int) Math.min(90f, 66f / Math.max(0.5f, le.getHeight()));
            InventoryScreen.drawEntity(c, mx0, my0, mx0 + mw, my0 + mh, sz, 0.0625f, (float) (vmx * s), (float) (vmy * s), le);
        } else {
            l(c, mode == 2 ? "brak peta" : "brak modelu", mx0 + 8, my0 + mh / 2 - 4, col(Theme.sub(), f));
        }

        // dane obok modelu
        int ix = rx + 90, iw = RW - 100;
        String dist = String.format("%.0fm", client.player != null ? p.distanceTo(client.player) : 0);
        t(c, Fx.fit(textRenderer, sel, iw - tw(dist) - 6), ix, py + 9, col(BLUE, f));
        t(c, dist, ix + iw - tw(dist), py + 9, col(Theme.sub(), f));

        GearInfo.Hp hp = info.hp();
        int hc = hp.value() >= 20 ? Theme.good() : (hp.value() >= 10 ? Theme.warn() : Theme.bad());
        String hpTxt = "HP " + fmt(hp.value()) + (hp.absorb() > 0.5f ? " +" + fmt(hp.absorb()) : "");
        t(c, hpTxt, ix, py + 21, col(hc, f));
        t(c, "(" + hp.source() + ")", ix + tw(hpTxt) + 5, py + 21, col(Theme.sub(), f));
        t(c, Fx.fit(textRenderer, hp.debug(), iw), ix, py + 32, col(Theme.sub(), 0.8f * f));

        if (mode == 2) {
            l(c, "pet:", ix, py + 46, col(Theme.a1(), f));
            if (info.pet() != null) {
                t(c, Fx.fit(textRenderer, info.petName(), iw), ix, py + 57, col(Theme.text(), f));
            } else {
                t(c, "brak (nie wykryto)", ix, py + 57, col(Theme.sub(), f));
            }
        } else if (mode == 1) {
            l(c, "kostium:", ix, py + 46, col(Theme.a1(), f));
            t(c, Fx.fit(textRenderer, info.costume() != null ? info.costume() : "brak", iw), ix, py + 57,
                    col(info.costume() != null ? Theme.text() : Theme.sub(), f));
        } else {
            l(c, "set:", ix, py + 46, col(Theme.a1(), f));
            t(c, Fx.fit(textRenderer, info.set(), iw), ix, py + 57, col(Theme.text(), f));
        }
        if (mode != 2) {
            l(c, "pet:", ix, py + 69, col(Theme.a1(), f));
            t(c, Fx.fit(textRenderer, info.pet() != null ? info.petName() : "brak", iw - lw("pet:") - 5),
                    ix + lw("pet:") + 5, py + 69, col(info.pet() != null ? Theme.text() : Theme.sub(), f));
        }

        button(c, ix, py + 82, iw, mode == 1 ? "pokaż seta" : "pokaż kostium", mode == 1, vmx, vmy, f);
        button(c, ix, py + 100, iw, mode == 2 ? "pokaż gracza" : "pokaż peta", mode == 2, vmx, vmy, f);

        if (mode == 2 && info.pet() == null && !info.nearby().isEmpty()) {
            // podpowiedź: co jest obok gracza (do dopasowania wykrywania)
            int ny = py + 118;
            l(c, "byty obok gracza:", rx + 10, ny, col(Theme.sub(), f));
            ny += 11;
            for (String n : info.nearby()) {
                t(c, Fx.fit(textRenderer, "· " + n, RW - 20), rx + 10, ny, col(Theme.sub(), f));
                ny += 10;
            }
        }

        // sloty
        int y = py + 123;
        if (!(mode == 2 && info.pet() == null && !info.nearby().isEmpty())) {
            c.fill(rx + 10, py + 118, rx + RW - 10, py + 119, col(Theme.line(), f));
            String[] labels = {"hełm", "napierśnik", "spodnie", "buty", "ręka", "druga ręka"};
            for (int i = 0; i < info.pieces().length; i++) {
                boolean hand = i >= 4;
                int rh = hand ? 30 : 22;
                gearRow(c, info.pieces()[i], labels[i], hand, rx + 10, y, RW - 20, rh, vmx, vmy, f);
                y += rh;
            }
        }

        // pasek: ostatnio trzymane
        int sy = py + 284;
        l(c, "ostatnio trzymane (pasek)", rx + 10, py + 273, col(Theme.a1(), f));
        java.util.LinkedHashMap<String, ItemStack> seen = Radar.seen.get(sel);
        String heldKey = p.getMainHandStack().isEmpty() ? null : Radar.key(p.getMainHandStack());
        int idx = 0;
        String hoverName = null;
        for (int i = 0; i < 9; i++) {
            int sx = rx + 10 + i * 26;
            c.fill(sx, sy, sx + 24, sy + 24, col(Theme.panel2(), f));
            c.fill(sx, sy + 23, sx + 24, sy + 24, col(Theme.line(), f));
        }
        if (seen != null) {
            for (java.util.Map.Entry<String, ItemStack> e : seen.entrySet()) {
                if (idx >= 9) break;
                int sx = rx + 10 + idx * 26;
                ItemStack st = e.getValue();
                if (e.getKey().equals(heldKey)) c.fill(sx - 1, sy - 1, sx + 25, sy + 25, col(BLUE, f));
                c.fill(sx, sy, sx + 24, sy + 24, col(Theme.panel2(), f));
                c.drawItem(st, sx + 4, sy + 4);
                c.drawStackOverlay(textRenderer, st, sx + 4, sy + 4);
                if (in(vmx, vmy, sx, sy, 24, 24)) {
                    hoverStack = st;
                    hoverName = st.getName().getString().replaceAll("§.", "");
                }
                idx++;
            }
        }
        if (idx == 0) l(c, "serwer nie wysyła całego paska – widać tylko to, co trzyma w rękach", rx + 10, sy + 28, col(Theme.sub(), 0.9f * f));
        else t(c, Fx.fit(textRenderer, hoverName != null ? hoverName : "najedź na przedmiot", RW - 20), rx + 10, sy + 28, col(Theme.sub(), f));
    }

    private void gearRow(DrawContext c, GearInfo.Piece pc, String label, boolean hand, int x, int y, int w, int rh,
                         float vmx, float vmy, float f) {
        boolean hov = in(vmx, vmy, x, y, w, rh);
        if (hov) c.fill(x, y, x + w, y + rh, col(Theme.panel2(), f));
        if (pc.stack().isEmpty()) {
            l(c, label + ": —", x + 22, y + (rh - 8) / 2, col(Theme.sub(), 0.7f * f));
            return;
        }
        if (hov) hoverStack = pc.stack();
        c.drawItem(pc.stack(), x + 2, y + (rh - 16) / 2);

        int tx = x + 24;
        String right = pc.dur() >= 0 ? pc.dur() + "%" : "";
        int rw = tw(right);
        t(c, Fx.fit(textRenderer, (pc.custom() ? "★ " : "") + pc.name(), w - 24 - rw - 6), tx, y + 2,
                col(pc.custom() ? Theme.warn() : Theme.text(), f));
        if (pc.dur() >= 0) {
            t(c, right, x + w - rw - 2, y + 2, col(pc.dur() > 60 ? Theme.good() : (pc.dur() > 30 ? Theme.warn() : Theme.bad()), f));
        }

        String line2 = "";
        if (pc.totem()) {
            line2 = "";                                      // totem: nic nie piszemy
        } else if (hand && pc.maxCount() > 1) {
            line2 = "x" + pc.count();                        // jabłka, koksy, perły itd. – ile trzyma
        } else {
            line2 = pc.ench();
        }
        String pctTxt = String.join("  ", pc.pct());

        int ly = y + 12;
        if (!line2.isEmpty()) {
            t(c, Fx.fit(textRenderer, line2, w - 24), tx, ly, col(hand && pc.maxCount() > 1 ? Theme.warn() : mix(Theme.a2(), Theme.text(), 0.35f), f));
            ly += 10;
        }
        if (!pctTxt.isEmpty() && (hand || line2.isEmpty())) {
            t(c, Fx.fit(textRenderer, pctTxt, w - 24), tx, ly, col(Theme.good(), f));
        }
    }

    // ================= sterowanie =================
    private void applyDrag(double vx) {
        int x0 = ox + 10, w = LW - 20;
        float k = (float) Math.max(0, Math.min(1, (vx - x0) / (double) w));
        if (drag == 1) Radar.intervalSec = Math.round((1 + k * 9) * 2) / 2f;
        else if (drag == 2) Radar.range = Math.min(200, 16 + 8 * Math.round(k * 184 / 8f));
        else if (drag == 3) Radar.minDist = Math.round(k * 20);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        layout();
        if (btn == 0) {
            if (in(mx, my, 6, 6, pill1W, 14)) { Theme.dark = !Theme.dark; return true; }
            if (in(mx, my, 6 + pill1W + 4, 6, pill2W, 14)) { Theme.anim = !Theme.anim; return true; }
        }
        double vx = mx / s, vy = my / s;
        int px = ox, py = oy;
        if (btn == 1) { Radar.selected = null; mode = 0; return true; }
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        // zakładki
        for (int i = 0; i < TABS.length; i++) {
            if (in(vx, vy, px + 10 + i * 92, py + 44, 88, 18)) { tab = i; return true; }
        }
        // gracze (mapa / lista)
        for (Hit h : hits) {
            if (in(vx, vy, h.x(), h.y(), h.w(), h.h())) { selectOrLock(h.name()); return true; }
        }
        // przyciski szczegółów
        int rx = px + LW + GAP, ix = rx + 90, iw = RW - 100;
        if (Radar.selected != null && findPlayer(Radar.selected) != null) {
            if (in(vx, vy, ix, py + 82, iw, 15)) { mode = mode == 1 ? 0 : 1; return true; }
            if (in(vx, vy, ix, py + 100, iw, 15)) { mode = mode == 2 ? 0 : 2; return true; }
        }
        // pasek TPA
        int tx = px + 10, ty = py + 298, tw0 = LW - 20;
        if (in(vx, vy, tx + tw0 - 154, ty + 4, 150, 22)) {
            if (Radar.target != null) Radar.tpaNow(client);
            return true;
        }
        if (in(vx, vy, tx + tw0 - 150 - 8 - 110, ty + 5, 110, 20)) { Radar.repeat = !Radar.repeat; return true; }

        int cx0 = px + 10, cy0 = py + 68, cw = LW - 20;
        if (tab == 0) {
            int mw = mapColsN * CELL;
            if (in(vx, vy, cx0 + mw - 22, cy0 + 4, 18, 18)) { Radar.zoom = Math.max(0, Radar.zoom - 1); return true; }
            if (in(vx, vy, cx0 + mw - 22, cy0 + 24, 18, 18)) { Radar.zoom = Math.min(STEPS.length - 1, Radar.zoom + 1); return true; }
            if (in(vx, vy, cx0 + mw - 22, cy0 + 44, 18, 18)) { panX = 0; panZ = 0; return true; }
            if (in(vx, vy, cx0, cy0, mw, mapRowsN * CELL)) { mapDrag = true; return true; }
        } else if (tab == 2) {
            if (in(vx, vy, cx0, cy0 + 4, cw, 22)) { Radar.serverWide = !Radar.serverWide; return true; }
            if (in(vx, vy, cx0, cy0 + 32, 176, 18)) { Radar.esp = !Radar.esp; return true; }
            if (in(vx, vy, cx0 + 184, cy0 + 32, 176, 18)) { Radar.nicks = !Radar.nicks; return true; }
            if (in(vx, vy, cx0, cy0 + 56, cw, 26)) { drag = 1; applyDrag(vx); return true; }
            if (in(vx, vy, cx0, cy0 + 88, cw, 26)) { drag = 2; applyDrag(vx); return true; }
            if (in(vx, vy, cx0, cy0 + 120, cw, 26)) { drag = 3; applyDrag(vx); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        layout();
        double vx = mx / s, vy = my / s;
        int cx0 = ox + 10, cy0 = oy + 68;
        if (tab == 0 && in(vx, vy, cx0, cy0, LW - 20, 224)) {
            Radar.zoom = Math.max(0, Math.min(STEPS.length - 1, Radar.zoom - (int) Math.signum(vAmount)));
        } else {
            scroll -= (int) Math.signum(vAmount);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        layout();
        if (drag != 0) { applyDrag(mx / s); return true; }
        if (mapDrag) {
            double bpp = mapStep / (double) CELL;
            panX -= (dx / s) * bpp;
            panZ -= (dy / s) * bpp;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        drag = 0;
        mapDrag = false;
        return super.mouseReleased(mx, my, btn);
    }
}
