package pl.szybki.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

/** Mały HUD + niebieskie podświetlenie (ESP) wybranego gracza i celu TPA. */
public final class RadarHud {
    private static final int BLUE = 0xFF3B82F6;
    private static final int BLUE_SOFT = 0x553B82F6;

    public static void render(DrawContext c, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null || mc.world == null) return;

        if (!(mc.currentScreen instanceof RadarScreen)) {
            int x = 6, y = 6, w = 156, h = 46;
            c.fill(x, y, x + w, y + h, 0xC00E1117);
            c.fill(x, y, x + 2, y + h, Radar.repeat ? 0xFF00E5FF : 0xFF4A5060);
            Fx.draw(c, mc.textRenderer, "SZYBKI RADAR", x + 8, y + 4, 0xFF00E5FF);
            String tg = Radar.target != null ? Radar.target : "brak celu";
            Fx.draw(c, mc.textRenderer, "cel: " + tg, x + 8, y + 15, 0xFFE8ECF4);
            Fx.draw(c, mc.textRenderer, (Radar.repeat ? "● powtarza" : "○ czeka") + "  " + Radar.lastSent,
                    x + 8, y + 26, Radar.repeat ? 0xFF00E676 : 0xFF8A93A6);
            Fx.draw(c, mc.textRenderer, "wykryto: " + Radar.targets.size(), x + 8, y + 36, 0xFF8A93A6);

            if (Radar.esp) {
                if (Radar.selected != null) esp(c, mc, Radar.selected, "");
                if (Radar.target != null && !Radar.target.equals(Radar.selected)) esp(c, mc, Radar.target, "cel • ");
            }
        }
    }

    private static void esp(DrawContext c, MinecraftClient mc, String name, String tag) {
        AbstractClientPlayerEntity p = null;
        for (AbstractClientPlayerEntity x : mc.world.getPlayers()) {
            if (x.getGameProfile().getName().equals(name)) { p = x; break; }
        }
        if (p == null || p == mc.player) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cam.getPos();
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;

        int W = c.getScaledWindowWidth(), H = c.getScaledWindowHeight();
        double tanH = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = W / (double) H;
        double dist = p.distanceTo(mc.player);

        double[] feet = project(p.getX(), p.getY(), p.getZ(), cp, rx, ry, rz, ux, uy, uz, fx, fy, fz, W, H, tanH, aspect);
        double[] head = project(p.getX(), p.getY() + 1.9, p.getZ(), cp, rx, ry, rz, ux, uy, uz, fx, fy, fz, W, H, tanH, aspect);
        GearInfo.Hp hp = GearInfo.hp(p, mc.world);
        String label = tag + name + "  " + String.format("%.0fm", dist) + "  " + GearInfo.fmt(hp.value()) + "hp";

        if (feet != null && head != null) {
            int top = (int) head[1], bottom = (int) feet[1];
            int h = Math.max(8, bottom - top);
            int w = Math.max(6, (int) (h * 0.48));
            int cx = (int) feet[0];
            int x0 = cx - w / 2, x1 = cx + w / 2;
            c.fill(x0, top, x1, bottom, BLUE_SOFT);
            c.fill(x0, top, x1, top + 2, BLUE);
            c.fill(x0, bottom - 2, x1, bottom, BLUE);
            c.fill(x0, top, x0 + 2, bottom, BLUE);
            c.fill(x1 - 2, top, x1, bottom, BLUE);
            Fx.line(c, W / 2, H - 2, cx, bottom, 0xAA3B82F6, 2);
            int tw = Fx.w(mc.textRenderer, label) + 8;
            c.fill(cx - tw / 2, top - 14, cx + tw / 2, top - 2, 0xDD0B1020);
            c.fill(cx - tw / 2, top - 14, cx + tw / 2, top - 13, BLUE);
            Fx.draw(c, mc.textRenderer, label, cx - tw / 2 + 4, top - 12, 0xFFFFFFFF);
        } else {
            // poza ekranem / za plecami – strzałka przy krawędzi
            double dx = p.getX() - cp.x, dy = p.getY() + 1 - cp.y, dz = p.getZ() - cp.z;
            double sx = dx * rx + dy * ry + dz * rz;
            double sy = dx * ux + dy * uy + dz * uz;
            double len = Math.hypot(sx, sy);
            if (len < 1e-4) { sx = 0; sy = -1; len = 1; }
            int ex = (int) (W / 2.0 + sx / len * (W / 2.0 - 40));
            int ey = (int) (H / 2.0 - sy / len * (H / 2.0 - 40));
            c.fill(ex - 6, ey - 6, ex + 7, ey + 7, BLUE);
            c.fill(ex - 4, ey - 4, ex + 5, ey + 5, 0xFF0B1020);
            int tw = Fx.w(mc.textRenderer, label) + 8;
            int lx = Math.max(2, Math.min(W - tw - 2, ex - tw / 2));
            c.fill(lx, ey + 9, lx + tw, ey + 21, 0xDD0B1020);
            Fx.draw(c, mc.textRenderer, label, lx + 4, ey + 11, 0xFFFFFFFF);
        }
    }

    private static double[] project(double x, double y, double z, Vec3d cp,
                                    double rx, double ry, double rz, double ux, double uy, double uz,
                                    double fx, double fy, double fz, int W, int H, double tanH, double aspect) {
        double dx = x - cp.x, dy = y - cp.y, dz = z - cp.z;
        double cx = dx * rx + dy * ry + dz * rz;
        double cy = dx * ux + dy * uy + dz * uz;
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < 0.1) return null;
        double sx = W / 2.0 * (1 + cx / (cz * tanH * aspect));
        double sy = H / 2.0 * (1 - cy / (cz * tanH));
        if (sx < -W || sx > 2 * W || sy < -H || sy > 2 * H) return null;
        return new double[]{sx, sy};
    }
}
