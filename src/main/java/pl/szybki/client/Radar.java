package pl.szybki.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.*;

public final class Radar {
    public static boolean repeat = false;       // powtarzaj /tpa do wybranego celu
    public static boolean serverWide = false;   // false = tylko załadowani wokół, true = + cała lista TAB
    public static boolean esp = true;           // niebieskie podświetlenie wybranego gracza w świecie
    public static boolean nicks = true;         // nicki przy graczach na mapie
    public static float intervalSec = 3f;
    public static int range = 200;
    public static int minDist = 4;              // bliżej = już jesteś u kolegi
    public static int zoom = 1;                 // indeks poziomu przybliżenia mapy

    public static String selected = null;       // gracz podglądany (1 klik)
    public static String target = null;         // cel TPA (2 kliki)
    public static String lastSent = "-";

    public static final List<Target> targets = new ArrayList<>();
    /** ostatnio widziane przedmioty w rękach gracza (serwer nie wysyła całego paska) */
    public static final Map<String, LinkedHashMap<String, ItemStack>> seen = new HashMap<>();

    private static Object lastWorld;
    private static long lastTpa = 0;

    public record Target(String name, double dist, double dx, double dz) {}

    public static String key(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()) + "|" + s.getName().getString();
    }

    public static void tick(MinecraftClient mc) {
        targets.clear();
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        if (mc.world != lastWorld) {
            seen.clear();
            lastWorld = mc.world;
        }

        String me = mc.player.getGameProfile().getName();
        Set<String> seenNames = new HashSet<>();

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            String n = p.getGameProfile().getName();

            // zapamiętaj co trzyma w rękach
            LinkedHashMap<String, ItemStack> m = seen.computeIfAbsent(n, k -> new LinkedHashMap<>());
            for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                ItemStack st = p.getEquippedStack(sl);
                if (st.isEmpty()) continue;
                String k = key(st);
                if (m.containsKey(k)) {
                    m.put(k, st.copy());
                } else {
                    m.put(k, st.copy());
                    if (m.size() > 9) {
                        Iterator<String> it = m.keySet().iterator();
                        it.next();
                        it.remove();
                    }
                }
            }

            double d = p.distanceTo(mc.player);
            if (d > range) continue;
            targets.add(new Target(n, d, p.getX() - mc.player.getX(), p.getZ() - mc.player.getZ()));
            seenNames.add(n);
        }

        if (serverWide) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String n = e.getProfile().getName();
                if (n.equals(me) || seenNames.contains(n)) continue;
                targets.add(new Target(n, -1, 0, 0));
            }
        }

        targets.sort(Comparator.comparingDouble(t -> t.dist() < 0 ? 1e9 : t.dist()));

        if (repeat && target != null) {
            long now = System.currentTimeMillis();
            if (now - lastTpa >= (long) (intervalSec * 1000)) {
                Target t = null;
                for (Target x : targets) if (x.name().equals(target)) { t = x; break; }
                if (t != null && (t.dist() < 0 || t.dist() >= minDist)) tpaNow(mc);
            }
        }
    }

    /** Jednorazowo wyślij /tpa do wybranego celu. */
    public static void tpaNow(MinecraftClient mc) {
        if (target == null || mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendChatCommand("tpa " + target);
        lastTpa = System.currentTimeMillis();
        lastSent = "/tpa " + target;
    }
}
