package pl.szybki.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class SzybkiClient implements ClientModInitializer {
    public static KeyBinding guiKey, radarKey, tpaKey, saveKey, restoreKey;
    public static volatile long lastTickNs = System.nanoTime();

    /** ułamek bieżącego ticka (0..1) – do płynnej interpolacji pozycji */
    public static float partial() {
        return Math.max(0f, Math.min(1f, (System.nanoTime() - lastTickNs) / 50_000_000f));
    }

    private static KeyBinding key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, code, "category.szybki"));
    }

    @Override
    public void onInitializeClient() {
        RadarConfig.load();
        Cfg.load();
        Mods.init();
        Cfg.apply();

        guiKey = key("key.szybki.gui", GLFW.GLFW_KEY_RIGHT_SHIFT);
        radarKey = key("key.szybki.radar", GLFW.GLFW_KEY_R);
        tpaKey = key("key.szybki.tpa", GLFW.GLFW_KEY_UNKNOWN);
        saveKey = key("key.szybki.save", GLFW.GLFW_KEY_UNKNOWN);
        restoreKey = key("key.szybki.restore", GLFW.GLFW_KEY_UNKNOWN);

        ClientTickEvents.START_CLIENT_TICK.register(mc -> Mods.FREECAM.onStartTick(mc));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (guiKey.wasPressed()) mc.setScreen(new ClickGuiScreen());
            while (radarKey.wasPressed()) mc.setScreen(new RadarScreen());
            while (tpaKey.wasPressed()) Radar.repeat = !Radar.repeat;
            while (saveKey.wasPressed()) Mods.ANTYKOSTKA.save(mc);
            while (restoreKey.wasPressed()) Mods.ANTYKOSTKA.restore(mc);

            Radar.tick(mc);
            for (Module m : Mods.ALL) {
                if (m.enabled) m.onTick(mc);
            }
            Mods.ANTYKOSTKA.pump(mc);
            lastTickNs = System.nanoTime();
        });

        HudRenderCallback.EVENT.register(HudRenderer::render);

        // przyciski AntyKostki w ekwipunku
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen && Mods.ANTYKOSTKA.enabled) {
                int left = (w - 176) / 2, top = (h - 166) / 2;
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(Lang.t("btn.save")),
                        b -> Mods.ANTYKOSTKA.save(client)).dimensions(left + 182, top + 6, 120, 18).build());
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(Lang.t("btn.restore")),
                        b -> Mods.ANTYKOSTKA.restore(client)).dimensions(left + 182, top + 28, 120, 18).build());
            }
        });
    }
}
