package pl.szybki.client;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final Category cat;
    public boolean enabled = false;
    protected boolean persist = true;   // czy stan włączenia zapisuje się w configu
    public final List<Setting> settings = new ArrayList<>();
    public float anim = 0f;             // animacja przełącznika w GUI

    protected Module(String name, Category cat) { this.name = name; this.cat = cat; }

    public boolean persists() { return persist; }
    public String desc() { return Lang.t("d." + name); }
    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onStartTick(MinecraftClient mc) {}
    public void onTick(MinecraftClient mc) {}

    protected Setting.Bool bool(String key, String nameKey, boolean def) {
        Setting.Bool s = new Setting.Bool(key, nameKey, def); settings.add(s); return s;
    }
    protected Setting.Slider slider(String key, String nameKey, double min, double max, double step, double def) {
        Setting.Slider s = new Setting.Slider(key, nameKey, min, max, step, def); settings.add(s); return s;
    }
    protected Setting.Choice choice(String key, String nameKey, int def, String... opts) {
        Setting.Choice s = new Setting.Choice(key, nameKey, def, opts); settings.add(s); return s;
    }
    protected Setting.Button button(String key, String nameKey, Runnable r) {
        Setting.Button s = new Setting.Button(key, nameKey, r); settings.add(s); return s;
    }
}
