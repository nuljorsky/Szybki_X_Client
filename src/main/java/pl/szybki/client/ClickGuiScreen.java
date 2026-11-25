package pl.szybki.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Menu SZYBKI CLIENT: animowane tło z latającymi mieczami, kategorie, moduły jak na screenie. */
public class ClickGuiScreen extends Screen {
    static final int W = 620, H = 352;
    static final int RH = 36;
    private static final int A1 = 0x00E5FF, A2 = 0x8A5CFF, GREEN = 0x22C55E;

    private static class Fly {
        float x, y, vx, vy, rot, vr, scale;
        ItemStack stack;
    }

    private float s = 1f;
    private int ox, oy;
    private long openAt, lastNs = System.nanoTime();
    private Category tab = Category.CHEATS;
    private float tabPos = 0;
    private int scroll = 0;
    private Module settingsFor = null;
    private Setting.Slider dragSlider = null;
    private int dragX, dragW;
    private final List<Fly> fly = new ArrayList<>();
    private final Random rnd = new Random();

    public ClickGuiScreen() { super(Text.literal("SZYBKI CLIENT")); }

    @Override protected void init() {
        openAt = System.currentTimeMillis();
        if (fly.isEmpty()) {
            ItemStack[] swords = {
                    new ItemStack(Items.WOODEN_SWORD), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.IRON_SWORD),
                    new ItemStack(Items.GOLDEN_SWORD), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_SWORD)};
            for (int i = 0; i < 24; i++) {
                Fly f = new Fly();
                f.stack = swords[rnd.nextInt(swords.length)];
                f.x = rnd.nextFloat() * Math.max(1, width);
                f.y = rnd.nextFloat() * Math.max(1, height);
                f.vx = (rnd.nextFloat() - 0.5f) * 30f;
                f.vy = 14f + rnd.nextFloat() * 40f;
                f.rot = rnd.nextFloat() * 360f;
                f.vr = (rnd.nextFloat() - 0.5f) * 90f;
                f.scale = 1.4f + rnd.nextFloat() * 2.4f;
                fly.add(f);
            }
        }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mx, int my, float d) { }
    @Override public void removed() { Cfg.save(); }

    static int col(int rgb, float a) {
        return (Math.max(5, Math.min(255, (int) (a * 255))) << 24) | (rgb & 0xFFFFFF);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void layout() {
        s = Math.min(1f, Math.min((width - 6f) / W, (height - 6f) / H));
        ox = (int) ((width / s - W) / 2);
        oy = (int) ((height / s - H) / 2);
    }

    private void t(DrawContext c, String str, int x, int y, int color) { Fx.draw(c, textRenderer, str, x, y, color); }
    private int tw(String str) { return Fx.w(textRenderer, str); }

    private List<Module> mods() {
        List<Module> l = new ArrayList<>();
        for (Module m : Mods.ALL) if (m.cat == tab) l.add(m);
        return l;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        layout();
        long nowNs = System.nanoTime();
        float dt = Math.min(0.05f, (nowNs - lastNs) / 1e9f);
        lastNs = nowNs;
        long now = System.currentTimeMillis();
        float time = (now % 600000L) / 1000f;
        float f = Math.min(1f, (now - openAt) / 280f);
        f = 1 - (1 - f) * (1 - f) * (1 - f);

        // tło: gradient + latające miecze
        c.fillGradient(0, 0, width, height, 0xFF070B16, 0xFF1A0B30);
        for (Fly fl : fly) {
            fl.x += fl.vx * dt; fl.y += fl.vy * dt; fl.rot += fl.vr * dt;
            if (fl.y > height + 60) { fl.y = -60; fl.x = rnd.nextFloat() * width; }
            if (fl.x < -60) fl.x = width + 60;
            if (fl.x > width + 60) fl.x = -60;
            c.getMatrices().push();
            c.getMatrices().translate(fl.x, fl.y, 0);
            c.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(fl.rot));
            c.getMatrices().scale(fl.scale, fl.scale, 1f);
            c.drawItem(fl.stack, -8, -8);
            c.getMatrices().pop();
        }
        c.fill(0, 0, width, height, col(0x050814, 0.62f * f));

        float vmx = mx / s, vmy = my / s;
        int px = ox, py = oy + (int) ((1 - f) * 18);

        c.getMatrices().push();
        c.getMatrices().scale(s, s, 1f);

        // okno
        int border = mix(A1, A2, (float) (Math.sin(time) * 0.5 + 0.5));
        c.fill(px - 1, py - 1, px + W + 1, py + H + 1, col(border, 0.8f * f));
        c.fill(px, py, px + W, py + H, col(0x0B0F19, 0.82f * f));
        c.fillGradient(px, py, px + W, py + 44, col(0x1B2236, f), col(0x0B0F19, 0.0f + 0.82f * f));

        // logo
        c.getMatrices().push();
        c.getMatrices().translate(px + 14, py + 11, 0);
        c.getMatrices().scale(1.7f, 1.7f, 1f);
        String title = "SZYBKI CLIENT";
        int tx = 0;
        for (int i = 0; i < title.length(); i++) {
            String ch = title.substring(i, i + 1);
            int cc = mix(A1, A2, (float) (Math.sin(time * 2.5 - i * 0.55) * 0.5 + 0.5));
            Fx.draw(c, textRenderer, ch, tx, 0, col(cc, f));
            tx += tw(ch);
        }
        c.getMatrices().pop();

        // języki
        int lx = px + W - 12;
        for (int i = Lang.NAMES.length - 1; i >= 0; i--) {
            String n = Lang.NAMES[i];
            int w = tw(n) + 14;
            lx -= w;
            boolean act = Lang.cur == i, hov = in(vmx, vmy, lx, py + 14, w, 18);
            c.fill(lx, py + 14, lx + w, py + 32, col(act ? A1 : (hov ? 0x2A3242 : 0x161B27), act ? 0.28f * f : f));
            if (act) c.fill(lx, py + 31, lx + w, py + 32, col(A1, f));
            t(c, n, lx + 7, py + 19, col(act ? A1 : 0xC8CEDC, f));
            lx -= 4;
        }

        // kategorie
        int tabX = px + 14;
        float target = 0;
        int[] tabXs = new int[Category.values().length], tabWs = new int[Category.values().length];
        for (int i = 0; i < Category.values().length; i++) {
            String n = Lang.t(Category.values()[i].key);
            int w = tw(n) + 28;
            tabXs[i] = tabX; tabWs[i] = w;
            boolean act = tab == Category.values()[i], hov = in(vmx, vmy, tabX, py + 50, w, 22);
            if (hov && !act) c.fill(tabX, py + 50, tabX + w, py + 72, col(0x161B27, f));
            t(c, n, tabX + 14, py + 57, col(act ? 0xFFFFFF : 0x8A93A6, f));
            tabX += w + 4;
        }
        int ti = tab.ordinal();
        float tx0 = tabXs[ti], tw0 = tabWs[ti];
        tabPos += (tx0 - tabPos) * 0.3f;
        c.fill((int) tabPos, py + 70, (int) tabPos + (int) tw0, py + 72, col(mix(A1, A2, 0.5f), f));
        c.fill(px + 14, py + 72, px + W - 14, py + 73, col(0x2A3242, f));

        // lista modułów (2 kolumny jak na screenie)
        List<Module> list = mods();
        int areaX = px + 14, areaY = py + 80, areaW = W - 28, areaH = H - 80 - 24;
        int colW = (areaW - 8) / 2;
        int rows = (list.size() + 1) / 2;
        int maxScroll = Math.max(0, rows * (RH + 5) - areaH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (list.isEmpty()) t(c, Lang.t("ui.empty"), areaX, areaY + 6, col(0x8A93A6, f));
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int x = areaX + (i % 2) * (colW + 8);
            int y = areaY + (i / 2) * (RH + 5) - scroll;
            if (y + RH < areaY || y > areaY + areaH) continue;
            boolean hov = settingsFor == null && in(vmx, vmy, x, y, colW, RH) && vmy >= areaY && vmy < areaY + areaH;
            m.anim += ((m.enabled ? 1f : 0f) - m.anim) * 0.25f;
            c.fill(x, y, x + colW, y + RH, col(hov ? 0x2A3347 : 0x1A2030, (hov ? 0.82f : 0.66f) * f));
            if (m.enabled) c.fill(x, y, x + 3, y + RH, col(mix(A1, A2, 0.5f), f));
            t(c, m.name, x + 12, y + 7, col(0xFFFFFF, f));
            t(c, Fx.fit(textRenderer, m.desc(), colW - 90), x + 12, y + 20, col(0x8A93A6, f));
            // przełącznik
            int sx = x + colW - 42, sy = y + (RH - 16) / 2;
            int track = mix(0x3A404E, GREEN, m.anim);
            c.fill(sx, sy, sx + 30, sy + 16, col(track, f));
            int kx = sx + 2 + (int) (m.anim * 14);
            c.fill(kx, sy + 2, kx + 12, sy + 14, col(0xFFFFFF, f));
            // kropki = są ustawienia (prawy klik)
            if (!m.settings.isEmpty()) {
                for (int d = 0; d < 3; d++) c.fill(sx - 12, sy + 3 + d * 4, sx - 10, sy + 5 + d * 4, col(0x8A93A6, f));
            }
        }

        t(c, Fx.fit(textRenderer, Lang.t("ui.hint"), W - 28), px + 14, py + H - 16, col(0x5A6478, f));

        if (settingsFor != null) drawSettings(c, px, py, vmx, vmy, f);

        c.getMatrices().pop();
    }

    static int mix(int a, int b, float k) { return RadarScreen.mix(a, b, k); }

    // ------------------------------------------------------------------ ustawienia modułu
    private int panelX(int px) { return px + (W - 340) / 2; }
    private int panelH() { return 62 + Math.max(1, settingsFor.settings.size()) * 28; }
    private int panelY(int py) { return py + (H - panelH()) / 2; }

    private void drawSettings(DrawContext c, int px, int py, float vmx, float vmy, float f) {
        c.fill(px, py, px + W, py + H, col(0x000000, 0.6f));
        int x = panelX(px), y = panelY(py), w = 340, h = panelH();
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, col(A1, 0.9f));
        c.fill(x, y, x + w, y + h, col(0x0E1320, 0.98f));
        t(c, settingsFor.name + " – " + Lang.t("ui.settings"), x + 12, y + 10, col(0xFFFFFF, 1f));
        t(c, Fx.fit(textRenderer, settingsFor.desc(), w - 24), x + 12, y + 24, col(0x8A93A6, 1f));
        int ry = y + 44;
        if (settingsFor.settings.isEmpty()) t(c, Lang.t("ui.nosettings"), x + 12, ry + 4, col(0x8A93A6, 1f));
        for (Setting st : settingsFor.settings) {
            String nm = Lang.t(st.nameKey);
            if (st instanceof Setting.Bool b) {
                t(c, nm, x + 12, ry + 6, col(0xE8ECF4, 1f));
                int sx = x + w - 46;
                c.fill(sx, ry + 4, sx + 30, ry + 20, col(b.v ? GREEN : 0x3A404E, 1f));
                int kx = sx + 2 + (b.v ? 14 : 0);
                c.fill(kx, ry + 6, kx + 12, ry + 18, col(0xFFFFFF, 1f));
            } else if (st instanceof Setting.Slider sl) {
                String v = sl.step < 1 ? String.format("%.1f", sl.v) : String.valueOf((int) Math.round(sl.v));
                t(c, nm, x + 12, ry, col(0xE8ECF4, 1f));
                t(c, v, x + w - 12 - tw(v), ry, col(A1, 1f));
                c.fill(x + 12, ry + 13, x + w - 12, ry + 17, col(0x232938, 1f));
                int fw = (int) ((w - 24) * sl.ratio());
                for (int i = 0; i < fw; i++) c.fill(x + 12 + i, ry + 13, x + 13 + i, ry + 17, col(mix(A1, A2, i / (float) (w - 24)), 1f));
                c.fill(x + 12 + fw - 2, ry + 10, x + 12 + fw + 3, ry + 20, col(0xFFFFFF, 1f));
            } else if (st instanceof Setting.Choice ch) {
                t(c, nm, x + 12, ry + 6, col(0xE8ECF4, 1f));
                String v = Lang.t(ch.get());
                int bw = tw(v) + 16;
                boolean hov = in(vmx, vmy, x + w - 12 - bw, ry + 2, bw, 18);
                c.fill(x + w - 12 - bw, ry + 2, x + w - 12, ry + 20, col(hov ? 0x2A3347 : 0x1A2030, 1f));
                t(c, v, x + w - 12 - bw + 8, ry + 7, col(A1, 1f));
            } else if (st instanceof Setting.Button) {
                boolean hov = in(vmx, vmy, x + 12, ry + 1, w - 24, 20);
                c.fill(x + 11, ry, x + w - 11, ry + 22, col(0x4ADE80, hov ? 0.95f : 0.55f));
                c.fill(x + 12, ry + 1, x + w - 12, ry + 21, col(hov ? 0x22C55E : 0x15803D, 1f));
                t(c, nm, x + (w - tw(nm)) / 2, ry + 7, col(0xFFFFFF, 1f));
            }
            ry += 28;
        }
        int cw = tw(Lang.t("ui.close")) + 20;
        boolean hov = in(vmx, vmy, x + w - cw - 10, y + h - 24, cw, 18);
        c.fill(x + w - cw - 10, y + h - 24, x + w - 10, y + h - 6, col(hov ? 0x2A3347 : 0x1A2030, 1f));
        t(c, Lang.t("ui.close"), x + w - cw, y + h - 19, col(0xE8ECF4, 1f));
    }

    // ------------------------------------------------------------------ sterowanie
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        layout();
        double vx = mx / s, vy = my / s;
        int px = ox, py = oy;

        if (settingsFor != null) {
            int x = panelX(px), y = panelY(py), w = 340, h = panelH();
            int cw = tw(Lang.t("ui.close")) + 20;
            if (!in(vx, vy, x, y, w, h) || in(vx, vy, x + w - cw - 10, y + h - 24, cw, 18)) {
                settingsFor = null; Cfg.save(); return true;
            }
            int ry = y + 44;
            for (Setting st : settingsFor.settings) {
                if (st instanceof Setting.Bool b && in(vx, vy, x + 8, ry, w - 16, 24)) { b.v = !b.v; }
                else if (st instanceof Setting.Slider sl && in(vx, vy, x + 8, ry - 2, w - 16, 26)) {
                    dragSlider = sl; dragX = x + 12; dragW = w - 24;
                    sl.setFromRatio((vx - dragX) / dragW);
                }
                else if (st instanceof Setting.Choice ch && in(vx, vy, x + 8, ry, w - 16, 24)) { ch.next(); }
                else if (st instanceof Setting.Button bt && in(vx, vy, x + 8, ry, w - 16, 24)) { bt.run.run(); }
                ry += 28;
            }
            return true;
        }

        // języki
        int lx = px + W - 12;
        for (int i = Lang.NAMES.length - 1; i >= 0; i--) {
            int w = tw(Lang.NAMES[i]) + 14;
            lx -= w;
            if (in(vx, vy, lx, py + 14, w, 18)) { Lang.cur = i; Cfg.save(); return true; }
            lx -= 4;
        }
        // kategorie
        int tabX = px + 14;
        for (Category cat : Category.values()) {
            int w = tw(Lang.t(cat.key)) + 28;
            if (in(vx, vy, tabX, py + 50, w, 22)) { tab = cat; scroll = 0; return true; }
            tabX += w + 4;
        }
        // moduły
        List<Module> list = mods();
        int areaX = px + 14, areaY = py + 80, areaW = W - 28, areaH = H - 80 - 24;
        int colW = (areaW - 8) / 2;
        for (int i = 0; i < list.size(); i++) {
            int x = areaX + (i % 2) * (colW + 8);
            int y = areaY + (i / 2) * (RH + 5) - scroll;
            if (in(vx, vy, x, y, colW, RH) && vy >= areaY && vy < areaY + areaH) {
                if (btn == 0) { list.get(i).toggle(); Cfg.save(); }
                else if (btn == 1) settingsFor = list.get(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragSlider != null) {
            layout();
            dragSlider.setFromRatio((mx / s - dragX) / dragW);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (dragSlider != null) { dragSlider = null; Cfg.save(); }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll -= (int) Math.signum(v) * 24;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SzybkiClient.guiKey != null && SzybkiClient.guiKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
