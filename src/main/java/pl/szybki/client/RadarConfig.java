package pl.szybki.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Zapis ustawień + reguły rozpoznawania setów (plik: .minecraft/config/szybkiradar.json). */
public final class RadarConfig {
    public static class Rule {
        public String contains;
        public String label;
        public Rule() {}
        public Rule(String c, String l) { contains = c; label = l; }
    }

    static class Data {
        boolean serverWide = false;
        boolean esp = true;
        boolean nicks = true;
        boolean dark = true;
        boolean anim = true;
        float intervalSec = 3f;
        int range = 200;
        int minDist = 4;
        int zoom = 1;
        List<Rule> setRules;
    }

    public static List<Rule> rules = defaults();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static List<Rule> defaults() {
        List<Rule> l = new ArrayList<>();
        // "contains" = fragment nazwy przedmiotu (bez różnicy wielkości liter), kolejność ma znaczenie
        l.add(new Rule("anarchiczny ii", "Anarchiczny 2"));
        l.add(new Rule("anarchiczny 2", "Anarchiczny 2"));
        l.add(new Rule("anarchiczny i", "Anarchiczny 1"));
        l.add(new Rule("anarchiczny 1", "Anarchiczny 1"));
        l.add(new Rule("anarchiczn", "Anarchiczny"));
        return l;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("szybkiradar.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Data d = GSON.fromJson(Files.readString(f), Data.class);
                if (d != null) {
                    Radar.serverWide = d.serverWide;
                    Radar.esp = d.esp;
                    Radar.nicks = d.nicks;
                    Theme.dark = d.dark;
                    Theme.anim = d.anim;
                    Radar.intervalSec = d.intervalSec;
                    Radar.range = Math.max(16, Math.min(200, d.range));
                    Radar.minDist = d.minDist;
                    Radar.zoom = Math.max(0, Math.min(3, d.zoom));
                    if (d.setRules != null && !d.setRules.isEmpty()) rules = d.setRules;
                }
            } else {
                save();
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Data d = new Data();
            d.serverWide = Radar.serverWide;
            d.esp = Radar.esp;
            d.nicks = Radar.nicks;
            d.dark = Theme.dark;
            d.anim = Theme.anim;
            d.intervalSec = Radar.intervalSec;
            d.range = Radar.range;
            d.minDist = Radar.minDist;
            d.zoom = Radar.zoom;
            d.setRules = rules;
            Files.writeString(file(), GSON.toJson(d));
        } catch (Exception ignored) {
        }
    }
}
