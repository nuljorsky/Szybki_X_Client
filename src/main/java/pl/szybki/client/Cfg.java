package pl.szybki.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Zapis ustawień clienta: .minecraft/config/szybkiclient.json */
public final class Cfg {
    static class Data {
        int lang = 0;
        Map<String, Boolean> enabled = new HashMap<>();
        Map<String, Boolean> bools = new HashMap<>();
        Map<String, Double> nums = new HashMap<>();
        List<String> friends = new ArrayList<>();
        String nielotName = "nielot";   // fragment nazwy wędki (bez różnicy wielkości liter)
    }

    static Data d = new Data();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("szybkiclient.json"); }

    public static boolean isFriend(String n) {
        for (String f : d.friends) if (f.equalsIgnoreCase(n)) return true;
        return false;
    }

    /** @return true gdy dodano, false gdy usunięto */
    public static boolean toggleFriend(String n) {
        for (Iterator<String> it = d.friends.iterator(); it.hasNext(); ) {
            if (it.next().equalsIgnoreCase(n)) { it.remove(); save(); return false; }
        }
        d.friends.add(n);
        save();
        return true;
    }

    public static String nielotName() { return d.nielotName == null ? "nielot" : d.nielotName.toLowerCase(Locale.ROOT); }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Data x = GSON.fromJson(Files.readString(f), Data.class);
            if (x == null) return;
            d = x;
            if (d.enabled == null) d.enabled = new HashMap<>();
            if (d.bools == null) d.bools = new HashMap<>();
            if (d.nums == null) d.nums = new HashMap<>();
            if (d.friends == null) d.friends = new ArrayList<>();
            Lang.cur = Math.max(0, Math.min(2, d.lang));
        } catch (Exception ignored) {
        }
    }

    /** po utworzeniu modułów: wczytaj ich stan i ustawienia */
    public static void apply() {
        for (Module m : Mods.ALL) {
            Boolean e = d.enabled.get(m.name);
            if (e != null && e && m.persists()) m.enabled = true;
            for (Setting s : m.settings) {
                String k = m.name + "." + s.key;
                if (s instanceof Setting.Bool b && d.bools.containsKey(k)) b.v = d.bools.get(k);
                else if (s instanceof Setting.Slider sl && d.nums.containsKey(k)) sl.v = d.nums.get(k);
                else if (s instanceof Setting.Choice c && d.nums.containsKey(k)) c.i = (int) Math.round(d.nums.get(k));
            }
        }
    }

    public static void save() {
        try {
            d.lang = Lang.cur;
            for (Module m : Mods.ALL) {
                d.enabled.put(m.name, m.enabled);
                for (Setting s : m.settings) {
                    String k = m.name + "." + s.key;
                    if (s instanceof Setting.Bool b) d.bools.put(k, b.v);
                    else if (s instanceof Setting.Slider sl) d.nums.put(k, sl.v);
                    else if (s instanceof Setting.Choice c) d.nums.put(k, (double) c.i);
                }
            }
            Files.writeString(file(), GSON.toJson(d));
        } catch (Exception ignored) {
        }
    }
}
