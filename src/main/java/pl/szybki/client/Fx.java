package pl.szybki.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

/** Styl tekstu całego moda: pogrubiony + opcjonalnie małe kapitaliki. Zawsze rozmiar 1:1 (ostry tekst). */
public final class Fx {
    private static final String SRC = "abcdefghijklmnopqrstuvwxyz";
    private static final String CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    public static String sc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            int i = SRC.indexOf(Character.toLowerCase(ch));
            b.append(i >= 0 ? CAPS.charAt(i) : Character.toLowerCase(ch));
        }
        return b.toString();
    }

    public static Text bold(String s) {
        return Text.literal(s).styled(st -> st.withBold(true));
    }

    public static int w(TextRenderer tr, String s) {
        return tr.getWidth(bold(s));
    }

    public static void draw(DrawContext c, TextRenderer tr, String s, int x, int y, int color) {
        c.drawText(tr, bold(s), x, y, color, Theme.dark);
    }

    public static String fit(TextRenderer tr, String s, int maxW) {
        if (maxW <= 0) return "";
        if (w(tr, s) <= maxW) return s;
        while (s.length() > 1 && w(tr, s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    public static void line(DrawContext c, int x0, int y0, int x1, int y1, int color, int thick) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            c.fill(x, y, x + thick, y + thick, color);
        }
    }

    /** Linia jako jeden obrócony prostokąt (szybka, dowolna długość). */
    public static void lineQ(DrawContext c, float x0, float y0, float x1, float y1, int color, float thick) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        int t = Math.max(1, Math.round(thick));
        c.getMatrices().push();
        c.getMatrices().translate(x0, y0, 0);
        c.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        c.fill(0, -t / 2, Math.round(len), t - t / 2, color);
        c.getMatrices().pop();
    }
}
