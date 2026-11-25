package pl.szybki.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Rejestr wszystkich modułów clienta. */
public final class Mods {
    public static final List<Module> ALL = new ArrayList<>();

    public static Hitboxes HITBOXES;
    public static Freecam FREECAM;
    public static FastPlace FASTPLACE;
    public static AutoLeave AUTOLEAVE;
    public static RadarMod RADAR;
    public static Tracers TRACERS;
    public static Nametags NAMETAGS;
    public static BlockEsp BLOCKESP;
    public static ElytraDetector ELYTRA;
    public static AntyKostka ANTYKOSTKA;
    public static AutoNielota NIELOTA;
    public static NameProtect NAMEPROTECT;
    public static MiddleClickFriend MCF;
    public static ArrayListMod ARRAYLIST;

    private static <T extends Module> T add(T m) { ALL.add(m); return m; }

    public static void init() {
        HITBOXES = add(new Hitboxes());
        FREECAM = add(new Freecam());
        FASTPLACE = add(new FastPlace());
        AUTOLEAVE = add(new AutoLeave());
        RADAR = add(new RadarMod());
        TRACERS = add(new Tracers());
        NAMETAGS = add(new Nametags());
        BLOCKESP = add(new BlockEsp());
        ELYTRA = add(new ElytraDetector());
        ANTYKOSTKA = add(new AntyKostka());
        NIELOTA = add(new AutoNielota());
        NAMEPROTECT = add(new NameProtect());
        MCF = add(new MiddleClickFriend());
        ARRAYLIST = add(new ArrayListMod());
    }

    static void msg(MinecraftClient mc, String text) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§b[SZYBKI] §f" + text), true);
    }

    // =====================================================================
    // SZYBKIE - CHEATY
    // =====================================================================

    /** Bicie graczy przez ściany (tylko gdy klikniesz – nic nie bije automatycznie). */
    public static final class Hitboxes extends Module {
        final Setting.Slider reach = slider("reach", "set.reach", 3.0, 6.0, 0.1, 3.0);
        final Setting.Bool playersOnly = bool("playersOnly", "set.playersOnly", false);

        Hitboxes() { super("Hitboxes", Category.CHEATS); }

        /** Szuka celu na linii patrzenia ignorując bloki. */
        public Entity find(MinecraftClient mc) {
            if (mc.player == null || mc.world == null) return null;
            Vec3d start = mc.player.getEyePos();
            Vec3d look = mc.player.getRotationVec(1f);
            double r = reach.v;
            Vec3d end = start.add(look.multiply(r));
            Box area = mc.player.getBoundingBox().stretch(look.multiply(r)).expand(1.0);
            Entity best = null;
            double bd = Double.MAX_VALUE;
            for (Entity e : mc.world.getOtherEntities(mc.player, area,
                    x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator()
                            && (!playersOnly.v || x instanceof PlayerEntity))) {
                Box bb = e.getBoundingBox().expand(e.getTargetingMargin());
                Optional<Vec3d> hit = bb.raycast(start, end);
                if (hit.isPresent()) {
                    double d = start.squaredDistanceTo(hit.get());
                    if (d < bd) { bd = d; best = e; }
                }
            }
            return best;
        }
    }

    /** Wolna kamera – postać stoi w miejscu, latasz kamerą. */
    public static final class Freecam extends Module {
        final Setting.Slider speed = slider("speed", "set.speed", 0.2, 4.0, 0.1, 0.8);
        public Vec3d pos = Vec3d.ZERO, prev = Vec3d.ZERO;

        Freecam() { super("Freecam", Category.CHEATS); persist = false; }

        @Override public void onEnable() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.gameRenderer != null && mc.player != null) pos = mc.gameRenderer.getCamera().getPos();
            prev = pos;
        }

        public Vec3d lerped(float t) { return prev.lerp(pos, t); }

        private boolean raw(MinecraftClient mc, KeyBinding kb) {
            if (mc.currentScreen != null) return false;
            try {
                InputUtil.Key k = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
                if (k.getCategory() == InputUtil.Type.KEYSYM) {
                    return InputUtil.isKeyPressed(mc.getWindow().getHandle(), k.getCode());
                }
            } catch (Exception ignored) {
            }
            return kb.isPressed();
        }

        @Override public void onStartTick(MinecraftClient mc) {
            if (!enabled || mc.player == null) return;
            boolean fw = raw(mc, mc.options.forwardKey), bk = raw(mc, mc.options.backKey);
            boolean lf = raw(mc, mc.options.leftKey), rt = raw(mc, mc.options.rightKey);
            boolean up = raw(mc, mc.options.jumpKey), dn = raw(mc, mc.options.sneakKey);
            // postać stoi: blokujemy klawisze ruchu
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);

            prev = pos;
            Vec3d look = mc.player.getRotationVec(1f);
            double yaw = Math.toRadians(mc.player.getYaw());
            Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3d mv = Vec3d.ZERO;
            if (fw) mv = mv.add(look);
            if (bk) mv = mv.subtract(look);
            if (rt) mv = mv.add(right);
            if (lf) mv = mv.subtract(right);
            if (up) mv = mv.add(0, 1, 0);
            if (dn) mv = mv.add(0, -1, 0);
            if (mv.lengthSquared() > 1e-6) pos = pos.add(mv.normalize().multiply(speed.v));
        }
    }

    public static final class FastPlace extends Module {
        final Setting.Bool blocksOnly = bool("blocksOnly", "set.blocksOnly", true);

        FastPlace() { super("FastPlace", Category.CHEATS); }

        public boolean active(MinecraftClient mc) {
            if (!enabled || mc.player == null) return false;
            if (!blocksOnly.v) return true;
            return mc.player.getMainHandStack().getItem() instanceof BlockItem
                    || mc.player.getOffHandStack().getItem() instanceof BlockItem;
        }
    }

    /** Wychodzi z serwera gdy ktoś się zbliży albo masz mało HP. */
    public static final class AutoLeave extends Module {
        final Setting.Slider dist = slider("dist", "set.leaveDist", 5, 100, 1, 30);
        final Setting.Slider hp = slider("hp", "set.leaveHp", 0, 40, 1, 0);
        final Setting.Bool friends = bool("friends", "set.ignoreFriends", true);

        AutoLeave() { super("AutoLeave", Category.CHEATS); persist = false; }

        @Override public void onTick(MinecraftClient mc) {
            if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
            if (hp.v > 0 && mc.player.getHealth() <= hp.v) { leave(mc, "HP " + Math.round(mc.player.getHealth())); return; }
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                String n = p.getGameProfile().getName();
                if (friends.v && Cfg.isFriend(n)) continue;
                if (p.distanceTo(mc.player) <= dist.v) { leave(mc, n); return; }
            }
        }

        private void leave(MinecraftClient mc, String why) {
            setEnabled(false);
            Cfg.save();
            mc.getNetworkHandler().getConnection().disconnect(Text.literal("[SZYBKI CLIENT] AutoLeave: " + why));
        }
    }

    // =====================================================================
    // SZYBKI - RADAR
    // =====================================================================

    public static final class RadarMod extends Module {
        RadarMod() {
            super("Radar", Category.RADAR);
            button("open", "btn.openRadar", () -> MinecraftClient.getInstance().setScreen(new RadarScreen()));
        }
    }

    public static final class Tracers extends Module {
        public final Setting.Slider range = slider("range", "set.range", 10, 200, 5, 100);
        public final Setting.Slider thick = slider("thick", "set.thick", 1, 4, 1, 2);
        public final Setting.Choice from = choice("from", "set.from", 0, "opt.bottom", "opt.center");
        public final Setting.Bool dist = bool("dist", "set.showDist", true);

        Tracers() { super("Tracers", Category.RADAR); }
    }

    public static final class Nametags extends Module {
        public final Setting.Slider scale = slider("scale", "set.scale", 0.6, 2.0, 0.1, 1.0);
        public final Setting.Bool hp = bool("hp", "set.showHp", true);
        public final Setting.Bool gear = bool("gear", "set.showGear", true);
        public final Setting.Bool hideVanilla = bool("hideVanilla", "set.hideVanilla", true);

        Nametags() { super("Nametags", Category.RADAR); }
    }

    public static final class BlockEsp extends Module {
        public record Found(BlockPos pos, int color, String label) {}

        public final Setting.Slider radius = slider("radius", "set.radius", 8, 64, 4, 32);
        public final Setting.Bool storage = bool("storage", "set.storage", true);
        public final Setting.Bool spawners = bool("spawners", "set.spawners", true);
        public final Setting.Bool ores = bool("ores", "set.ores", false);
        public volatile List<Found> found = new ArrayList<>();
        private int tick = 0;

        BlockEsp() { super("BlockESP", Category.RADAR); }

        @Override public void onDisable() { found = new ArrayList<>(); }

        @Override public void onTick(MinecraftClient mc) {
            if (mc.world == null || mc.player == null) return;
            if (++tick % 15 != 1) return;
            int rad = (int) radius.v;
            BlockPos pp = mc.player.getBlockPos();
            List<Found> out = new ArrayList<>();
            int cx0 = (pp.getX() - rad) >> 4, cx1 = (pp.getX() + rad) >> 4;
            int cz0 = (pp.getZ() - rad) >> 4, cz1 = (pp.getZ() + rad) >> 4;
            int bottom = mc.world.getBottomY(), top = bottom + mc.world.getHeight();
            int y0 = Math.max(bottom, pp.getY() - Math.min(rad, 32)), y1 = Math.min(top - 1, pp.getY() + Math.min(rad, 32));
            BlockPos.Mutable m = new BlockPos.Mutable();
            double r2 = (double) rad * rad;

            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    Chunk ch = mc.world.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (ch == null) continue;
                    if (storage.v || spawners.v) {
                        for (BlockPos bp : ch.getBlockEntityPositions()) {
                            if (bp.getSquaredDistance(pp) > r2) continue;
                            classify(ch.getBlockState(bp), bp, out);
                        }
                    }
                    if (ores.v) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                int wx = (cx << 4) + x, wz = (cz << 4) + z;
                                double dx = wx - pp.getX(), dz = wz - pp.getZ();
                                if (dx * dx + dz * dz > r2) continue;
                                for (int y = y0; y <= y1; y++) {
                                    m.set(wx, y, wz);
                                    BlockState st = ch.getBlockState(m);
                                    if (st.isAir()) continue;
                                    classifyOre(st, m, out);
                                }
                            }
                        }
                    }
                }
            }
            out.sort(Comparator.comparingDouble(f -> f.pos().getSquaredDistance(pp)));
            if (out.size() > 300) out = new ArrayList<>(out.subList(0, 300));
            found = out;
        }

        private void classify(BlockState st, BlockPos bp, List<Found> out) {
            Block b = st.getBlock();
            BlockPos p = bp.toImmutable();
            if (storage.v) {
                if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST) out.add(new Found(p, 0xFFD54F, "Chest"));
                else if (b == Blocks.ENDER_CHEST) out.add(new Found(p, 0xB388FF, "EnderChest"));
                else if (b == Blocks.BARREL) out.add(new Found(p, 0xFFB74D, "Barrel"));
                else if (b instanceof ShulkerBoxBlock) out.add(new Found(p, 0xFF80AB, "Shulker"));
            }
            if (spawners.v && b == Blocks.SPAWNER) out.add(new Found(p, 0xFF5252, "Spawner"));
        }

        private void classifyOre(BlockState st, BlockPos.Mutable m, List<Found> out) {
            Block b = st.getBlock();
            if (b == Blocks.ANCIENT_DEBRIS) out.add(new Found(m.toImmutable(), 0xA1887F, "Debris"));
            else if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) out.add(new Found(m.toImmutable(), 0x4DD0E1, "Diamond"));
            else if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) out.add(new Found(m.toImmutable(), 0x69F0AE, "Emerald"));
        }
    }

    /** Alarm "UWAŻAJ WLOTA!!!" gdy ktoś leci elytrą w zasięgu. */
    public static final class ElytraDetector extends Module {
        public final Setting.Slider range = slider("range", "set.range", 20, 200, 5, 100);
        public volatile String alertName = null;
        public volatile double alertDist = 0;
        public volatile long alertUntil = 0;
        private final Map<UUID, Vec3d> last = new HashMap<>();

        ElytraDetector() { super("ElytraDetector", Category.RADAR); }

        @Override public void onTick(MinecraftClient mc) {
            if (mc.world == null || mc.player == null) return;
            double best = Double.MAX_VALUE;
            String who = null;
            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                Vec3d cur = p.getPos();
                Vec3d prev = last.put(p.getUuid(), cur);
                if (prev == null) continue;
                double hs = Math.hypot(cur.x - prev.x, cur.z - prev.z);
                boolean elytra = p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
                double d = p.distanceTo(mc.player);
                if (elytra && !p.isOnGround() && hs > 0.5 && d <= range.v && d < best) {
                    best = d;
                    who = p.getGameProfile().getName();
                }
            }
            if (who != null) {
                alertName = who;
                alertDist = best;
                alertUntil = System.currentTimeMillis() + 1200;
            }
        }
    }

    // =====================================================================
    // SZYBKIE - WSPOMAGACZE
    // =====================================================================

    /** Zapisz układ ekwipunku i przywróć go, gdy ktoś Ci go przetasuje. */
    public static final class AntyKostka extends Module {
        private String[] saved = null;
        private final ArrayDeque<Integer> queue = new ArrayDeque<>();

        AntyKostka() {
            super("AntyKostka", Category.HELPERS);
            button("save", "btn.save", () -> save(MinecraftClient.getInstance()));
            button("restore", "btn.restore", () -> restore(MinecraftClient.getInstance()));
        }

        private static String sig(ItemStack s) {
            return s.isEmpty() ? "" : Registries.ITEM.getId(s.getItem()) + "|" + s.getName().getString();
        }

        private String[] snapshot(MinecraftClient mc) {
            String[] a = new String[46];
            for (int i = 9; i <= 45; i++) a[i] = sig(mc.player.playerScreenHandler.getSlot(i).getStack());
            return a;
        }

        public void save(MinecraftClient mc) {
            if (mc.player == null) return;
            saved = snapshot(mc);
            msg(mc, Lang.t("msg.saved"));
        }

        public void restore(MinecraftClient mc) {
            if (mc.player == null || mc.interactionManager == null) return;
            if (saved == null) { msg(mc, Lang.t("msg.nosaved")); return; }
            String[] cur = snapshot(mc);
            queue.clear();
            for (int i = 9; i <= 45; i++) {
                if (cur[i].equals(saved[i])) continue;
                int j = -1;
                for (int k = 9; k <= 45; k++) {
                    if (k != i && cur[k].equals(saved[i]) && !cur[k].equals(saved[k])) { j = k; break; }
                }
                if (j < 0) continue;
                String t = cur[i]; cur[i] = cur[j]; cur[j] = t;
                queue.add(j); queue.add(i); queue.add(j);   // zamiana miejscami trzema kliknięciami
            }
            msg(mc, queue.isEmpty() ? Lang.t("msg.already") : Lang.t("msg.restoring"));
        }

        /** wywoływane co tick (niezależnie od włączenia modułu) */
        public void pump(MinecraftClient mc) {
            if (queue.isEmpty() || mc.player == null || mc.interactionManager == null) return;
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) { queue.clear(); return; }
            for (int n = 0; n < 3 && !queue.isEmpty(); n++) {
                int slot = queue.poll();
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
            }
        }
    }

    /** Gdy gracz obok zakłada elytrę – wędka Nielot trafia do lewej ręki. */
    public static final class AutoNielota extends Module {
        final Setting.Slider dist = slider("dist", "set.nielotDist", 3, 30, 1, 10);
        private final Map<UUID, Boolean> had = new HashMap<>();
        private int cooldown = 0;

        AutoNielota() { super("AutoNielota", Category.HELPERS); }

        @Override public void onTick(MinecraftClient mc) {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) return;
            if (cooldown > 0) cooldown--;
            boolean trigger = false;
            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                boolean now = p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
                Boolean prev = had.put(p.getUuid(), now);
                if (prev != null && !prev && now && p.distanceTo(mc.player) <= dist.v) trigger = true;
            }
            if (trigger && cooldown == 0) {
                cooldown = 40;
                swapRod(mc);
            }
        }

        private boolean isRod(ItemStack s) {
            return s.isOf(Items.FISHING_ROD)
                    && s.getName().getString().toLowerCase(Locale.ROOT).contains(Cfg.nielotName());
        }

        private void swapRod(MinecraftClient mc) {
            if (isRod(mc.player.getOffHandStack())) return;
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
            for (int i = 9; i <= 44; i++) {
                if (isRod(mc.player.playerScreenHandler.getSlot(i).getStack())) {
                    // przycisk 40 = zamiana z lewą ręką
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, 40, SlotActionType.SWAP, mc.player);
                    return;
                }
            }
            msg(mc, Lang.t("msg.nielotNone"));
        }
    }

    /** Podmienia Twój nick w tekstach (czat, tab, ekran). */
    public static final class NameProtect extends Module {
        final Setting.Choice fake = choice("fake", "set.fakeName", 0, "Szybki", "Protected", "Gracz", "Hidden");
        public static volatile String myName = "";

        NameProtect() { super("NameProtect", Category.HELPERS); }

        @Override public void onTick(MinecraftClient mc) {
            myName = mc.player != null ? mc.player.getGameProfile().getName() : "";
        }

        public String replace(String s) {
            if (!enabled || myName.isEmpty() || s == null || !s.contains(myName)) return s;
            return s.replace(myName, fake.get());
        }
    }

    /** Środkowy przycisk myszy na graczu = znajomy. */
    public static final class MiddleClickFriend extends Module {
        private boolean was = false;

        MiddleClickFriend() { super("MiddleClickFriend", Category.HELPERS); }

        @Override public void onTick(MinecraftClient mc) {
            boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
            if (down && !was && mc.currentScreen == null && mc.targetedEntity instanceof PlayerEntity p) {
                String n = p.getGameProfile().getName();
                boolean added = Cfg.toggleFriend(n);
                msg(mc, Lang.t(added ? "msg.friendAdd" : "msg.friendRemove") + n);
            }
            was = down;
        }
    }

    public static final class ArrayListMod extends Module {
        ArrayListMod() { super("ArrayList", Category.HELPERS); }
    }
}
