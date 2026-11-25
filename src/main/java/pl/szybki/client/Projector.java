package pl.szybki.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Rzutowanie punktów świata na ekran (ESP, tracery, nametagi). */
public final class Projector {
    static double px, py, pz, rx, rz, ux, uy, uz, fx, fy, fz, tanH, aspect;
    public static int W, H;

    public static void begin(MinecraftClient mc, DrawContext c) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cam.getPos();
        px = cp.x; py = cp.y; pz = cp.z;
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        W = c.getScaledWindowWidth();
        H = c.getScaledWindowHeight();
        tanH = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        aspect = W / (double) H;
    }

    /** out: [0]=x ekranu, [1]=y ekranu, [2]=głębokość, [3]=prawo, [4]=góra. Zwraca true gdy punkt jest przed kamerą. */
    public static boolean project(double x, double y, double z, double[] out) {
        double dx = x - px, dy = y - py, dz = z - pz;
        double cx = dx * rx + dz * rz;
        double cy = dx * ux + dy * uy + dz * uz;
        double cz = dx * fx + dy * fy + dz * fz;
        out[2] = cz; out[3] = cx; out[4] = cy;
        if (cz < 0.05) return false;
        out[0] = W / 2.0 * (1 + cx / (cz * tanH * aspect));
        out[1] = H / 2.0 * (1 - cy / (cz * tanH));
        return true;
    }

    /** punkt przy krawędzi ekranu w kierunku obiektu (gdy jest za plecami) */
    public static int[] edge(double[] o, int margin) {
        double cx = o[3], cy = o[4];
        double len = Math.hypot(cx, cy);
        if (len < 1e-4) { cx = 0; cy = -1; len = 1; }
        return new int[]{(int) (W / 2.0 + cx / len * (W / 2.0 - margin)), (int) (H / 2.0 - cy / len * (H / 2.0 - margin))};
    }
}
