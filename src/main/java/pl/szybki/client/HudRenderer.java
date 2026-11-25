package pl.szybki.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Wszystko co client rysuje na ekranie: ESP, tracery, nametagi, alarm elytr, ArrayList. */
public final class HudRenderer {
    private static final double[] O = new double[5];
    private static final Map<UUID, float[]> hpCache = new HashMap<>();
    private static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    public static void render(DrawContext c, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (mc.currentScreen instanceof ClickGuiScreen || mc.currentScreen instanceof RadarScreen) return;
        TextRenderer tr = mc.textRenderer;
        Projector.begin(mc, c);
        float pt = SzybkiClient.partial();

        if (Mods.BLOCKESP.enabled) blocks(c, mc, tr);
        if (Mods.TRACERS.enabled) tracers(c, mc, tr, pt);
        if (Mods.NAMETAGS.enabled) nametags(c, mc, tr, pt);
        if (Mods.ELYTRA.enabled) alert(c, tr);
        if (Mods.RADAR.enabled) RadarHud.render(c, tc);
        if (Mods.ARRAYLIST.enabled) arrayList(c, tr);
    }

    // ---------------------------------------------------------------- BlockESP
    private static void blocks(DrawContext c, MinecraftClient mc, TextRenderer tr) {
        List<Mods.BlockEsp.Found> list = Mods.BLOCKESP.found;
        BlockPos pp = mc.player.getBlockPos();
        int labels = 0;
        for (Mods.BlockEsp.Found f : list) {
            BlockPos b = f.pos();
            double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
            boolean ok = true;
            for (int i = 0; i < 8 && ok; i++) {
                if (!Projector.project(b.getX() + (i & 1), b.getY() + ((i >> 1) & 1), b.getZ() + ((i >> 2) & 1), O)) { ok = false; break; }
                minX = Math.min(minX, O[0]); maxX = Math.max(maxX, O[0]);
                minY = Math.min(minY, O[1]); maxY = Math.max(maxY, O[1]);
            }
            if (!ok) continue;
            int x0 = (int) minX, y0 = (int) minY, x1 = Math.max(x0 + 3, (int) maxX), y1 = Math.max(y0 + 3, (int) maxY);
            if (x1 < 0 || y1 < 0 || x0 > Projector.W || y0 > Projector.H) continue;
            int col = 0xFF000000 | f.color();
            c.fill(x0, y0, x1, y1, (col & 0xFFFFFF) | 0x28000000);
            c.fill(x0, y0, x1, y0 + 1, col);
            c.fill(x0, y1 - 1, x1, y1, col);
            c.fill(x0, y0, x0 + 1, y1, col);
            c.fill(x1 - 1, y0, x1, y1, col);
            if (labels < 40) {
                labels++;
                String t = f.label() + " " + (int) Math.sqrt(b.getSquaredDistance(pp)) + "m";
                Fx.draw(c, tr, t, (x0 + x1) / 2 - Fx.w(tr, t) / 2, y0 - 10, col);
            }
        }
    }

    // ---------------------------------------------------------------- Tracers
    private static void tracers(DrawContext c, MinecraftClient mc, TextRenderer tr, float pt) {
        Mods.Tracers m = Mods.TRACERS;
        int sx = Projector.W / 2, sy = m.from.i == 0 ? Projector.H : Projector.H / 2;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            double d = p.distanceTo(mc.player);
            if (d > m.range.v) continue;
            Vec3d pos = p.getLerpedPos(pt);
            boolean front = Projector.project(pos.x, pos.y + p.getHeight() * 0.5, pos.z, O);
            int tx, ty;
            if (front) { tx = (int) O[0]; ty = (int) O[1]; }
            else { int[] e = Projector.edge(O, 24); tx = e[0]; ty = e[1]; }
            boolean fr = Cfg.isFriend(p.getGameProfile().getName());
            int col = fr ? 0xFF4ADE80 : hsv((float) Math.min(1.0, d / m.range.v) * 0.55f);
            Fx.lineQ(c, sx, sy, tx, ty, col, (float) m.thick.v);
            if (m.dist.v) {
                String t = (int) d + "m";
                int cx = Math.max(4, Math.min(Projector.W - Fx.w(tr, t) - 4, tx - Fx.w(tr, t) / 2));
                int cy = Math.max(2, Math.min(Projector.H - 12, ty + 4));
                Fx.draw(c, tr, t, cx, cy, col);
            }
        }
    }

    private static int hsv(float h) {   // 0 = czerwony ... 0.55 = niebieski
        int rgb = java.awt.Color.HSBtoRGB(h, 0.85f, 1f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- Nametags
    private static float hpOf(MinecraftClient mc, AbstractClientPlayerEntity p) {
        long now = System.nanoTime();
        float[] e = hpCache.get(p.getUuid());
        if (e == null || now - (long) e[1] > 400_000_000L) {
            float v = GearInfo.hp(p, mc.world).value();
            e = new float[]{v, now};
            hpCache.put(p.getUuid(), e);
        }
        return e[0];
    }

    private static void nametags(DrawContext c, MinecraftClient mc, TextRenderer tr, float pt) {
        Mods.Nametags m = Mods.NAMETAGS;
        float sc = (float) m.scale.v;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            double d = p.distanceTo(mc.player);
            if (d > 160) continue;
            Vec3d pos = p.getLerpedPos(pt);
            if (!Projector.project(pos.x, pos.y + p.getHeight() + 0.5, pos.z, O)) continue;
            String nick = p.getGameProfile().getName();
            boolean fr = Cfg.isFriend(nick);
            String dist = (int) d + "m";
            float hp = m.hp.v ? hpOf(mc, p) : -1;
            float maxHp = Math.max(20f, Math.max(p.getMaxHealth(), hp));
            int nw = Fx.w(tr, nick) + 8 + Fx.w(tr, dist);
            int pw = Math.max(nw, 84) + 10;
            int ph = 14 + (hp >= 0 ? 9 : 0) + (m.gear.v ? 14 : 0);
            int accent = fr ? 0x4ADE80 : (hp >= 0 ? hsv(Math.max(0f, Math.min(1f, hp / maxHp)) * 0.33f) & 0xFFFFFF : 0x3B82F6);

            c.getMatrices().push();
            c.getMatrices().translate((float) O[0], (float) O[1], 0);
            c.getMatrices().scale(sc, sc, 1f);
            int x0 = -pw / 2, y0 = -ph;
            c.fill(x0 - 1, y0 - 1, x0 + pw + 1, y0 + ph + 1, 0x99000000 | accent & 0xFFFFFF);
            c.fill(x0, y0, x0 + pw, y0 + ph, 0xE60B0F18);
            c.fill(x0, y0, x0 + 2, y0 + ph, 0xFF000000 | accent);
            Fx.draw(c, tr, nick, x0 + 6, y0 + 3, fr ? 0xFF4ADE80 : 0xFFFFFFFF);
            Fx.draw(c, tr, dist, x0 + pw - Fx.w(tr, dist) - 4, y0 + 3, 0xFF8A93A6);
            int yy = y0 + 14;
            if (hp >= 0) {
                int bw = pw - 12;
                c.fill(x0 + 6, yy, x0 + 6 + bw, yy + 4, 0xFF222733);
                c.fill(x0 + 6, yy, x0 + 6 + (int) (bw * Math.min(1f, hp / maxHp)), yy + 4, 0xFF000000 | accent);
                yy += 9;
            }
            if (m.gear.v) {
                c.getMatrices().push();
                c.getMatrices().translate(x0 + 4, yy, 0);
                c.getMatrices().scale(0.75f, 0.75f, 1f);
                for (int i = 0; i < GEAR.length; i++) {
                    ItemStack st = p.getEquippedStack(GEAR[i]);
                    if (!st.isEmpty()) c.drawItem(st, i * 15, 0);
                }
                c.getMatrices().pop();
            }
            c.getMatrices().pop();
        }
    }

    // ---------------------------------------------------------------- Elytra alarm
    private static void alert(DrawContext c, TextRenderer tr) {
        Mods.ElytraDetector e = Mods.ELYTRA;
        if (System.currentTimeMillis() > e.alertUntil || e.alertName == null) return;
        float blink = (float) (Math.sin(System.currentTimeMillis() / 90.0) * 0.5 + 0.5);
        int a = (int) (170 + 85 * blink);
        String msg = Lang.t("alert.elytra");
        int W = Projector.W;
        c.fill(0, 8, W, 54, 0x66200000);
        c.getMatrices().push();
        c.getMatrices().translate(W / 2f, 14, 0);
        c.getMatrices().scale(3f, 3f, 1f);
        Fx.draw(c, tr, msg, -Fx.w(tr, msg) / 2, 0, (a << 24) | 0xFF2020);
        c.getMatrices().pop();
        String sub = e.alertName + "  •  " + (int) e.alertDist + "m";
        Fx.draw(c, tr, sub, W / 2 - Fx.w(tr, sub) / 2, 42, 0xFFFFFFFF);
    }

    // ---------------------------------------------------------------- ArrayList
    private static void arrayList(DrawContext c, TextRenderer tr) {
        List<Module> on = new ArrayList<>();
        for (Module m : Mods.ALL) if (m.enabled && m != Mods.ARRAYLIST) on.add(m);
        on.sort((a, b) -> Integer.compare(Fx.w(tr, b.name), Fx.w(tr, a.name)));
        int y = 4;
        long t = System.currentTimeMillis();
        for (int i = 0; i < on.size(); i++) {
            String n = on.get(i).name;
            int w = Fx.w(tr, n);
            int x = Projector.W - w - 8;
            int col = RadarScreen.mix(0x00E5FF, 0x8A5CFF, (float) (Math.sin(t / 700.0 - i * 0.6) * 0.5 + 0.5));
            c.fill(x - 3, y - 1, Projector.W, y + 10, 0x99000000);
            c.fill(Projector.W - 2, y - 1, Projector.W, y + 10, 0xFF000000 | col);
            Fx.draw(c, tr, n, x, y, 0xFF000000 | col);
            y += 11;
        }
    }
}
