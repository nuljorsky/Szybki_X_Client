package pl.szybki.client;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Odczyt ekwipunku, HP i peta gracza widocznego dla klienta (to co serwer wysyła do Twojej gry). */
public final class GearInfo {

    /** dur = % wytrzymałości (-1 brak), pct = linie z "%" z opisu (lore) */
    public record Piece(ItemStack stack, String name, int dur, boolean custom, String ench,
                        List<String> pct, int count, int maxCount, boolean totem) {}

    public record Hp(float value, float absorb, String source, String debug) {}

    public record Info(Piece[] pieces, String set, String costume, Entity pet, String petName,
                       List<String> nearby, Hp hp) {}

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    private static final String[] SUFFIX = {"_helmet", "_chestplate", "_leggings", "_boots"};
    private static final Pattern HP1 = Pattern.compile("(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*(?:❤|♥|♡|hp|HP)");
    private static final Pattern HP2 = Pattern.compile("(?:❤|♥|♡)\\s*(\\d{1,3}(?:[.,]\\d{1,2})?)");

    private record E(String label, int lvl) {}

    static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "").trim();
    }

    static String fmt(float v) {
        return Math.abs(v - Math.round(v)) < 0.05f ? String.valueOf(Math.round(v)) : String.format("%.1f", v);
    }

    public static Info of(AbstractClientPlayerEntity p, ClientWorld w) {
        Piece[] pieces = new Piece[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) {
            pieces[i] = piece(p.getEquippedStack(SLOTS[i]));
        }

        // ---- zbroja: set (reguły + vanilla) kontra kostium (customowy / skórzany)
        boolean[] matched = new boolean[4];
        boolean[] costume = new boolean[4];
        Map<String, Integer> ruleCount = new LinkedHashMap<>();
        Map<String, Integer> mats = new LinkedHashMap<>();
        int nonEmpty = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = pieces[i].stack();
            if (s.isEmpty()) continue;
            nonEmpty++;
            String lower = pieces[i].name().toLowerCase(Locale.ROOT);
            boolean hit = false;
            for (RadarConfig.Rule r : RadarConfig.rules) {
                if (r.contains != null && !r.contains.isEmpty() && lower.contains(r.contains.toLowerCase(Locale.ROOT))) {
                    ruleCount.merge(r.label, 1, Integer::sum);
                    matched[i] = true;
                    hit = true;
                    break;
                }
            }
            if (hit) continue;
            String path = Registries.ITEM.getId(s.getItem()).getPath();
            if (pieces[i].custom() || path.startsWith("leather_")) {
                costume[i] = true;
            } else {
                for (String suf : SUFFIX) {
                    if (path.endsWith(suf)) { path = path.substring(0, path.length() - suf.length()); break; }
                }
                mats.merge(material(path), 1, Integer::sum);
            }
        }

        String set;
        if (nonEmpty == 0) {
            set = "brak zbroi";
        } else if (!ruleCount.isEmpty()) {
            set = best(ruleCount);
        } else if (!mats.isEmpty()) {
            set = best(mats);
        } else {
            set = "nie widać (kostium)";
        }

        List<String> cos = new ArrayList<>();
        for (int i = 0; i < 4; i++) if (costume[i]) cos.add(pieces[i].name());
        String costumeText = cos.isEmpty() ? null : String.join(", ", cos);

        // ---- pet
        String nickRaw = p.getGameProfile().getName();
        String nick = nickRaw.toLowerCase(Locale.ROOT);
        Entity pet = null;
        String petName = null;
        int bestScore = 0;
        double bestDist = 1e9;
        List<String> nearby = new ArrayList<>();
        for (Entity e : w.getEntities()) {
            if (e == p || e instanceof PlayerEntity || e instanceof ItemEntity) continue;
            double d = e.distanceTo(p);
            String cn = e.hasCustomName() ? strip(e.getCustomName().getString()) : "";
            String head = "";
            if (e instanceof ArmorStandEntity st) {
                ItemStack hs = st.getEquippedStack(EquipmentSlot.HEAD);
                if (!hs.isEmpty()) head = strip(hs.getName().getString());
            }
            if (d < 7 && nearby.size() < 4) {
                String tag = e.getType().getName().getString();
                if (!cn.isEmpty()) tag += " \"" + cn + "\"";
                if (!head.isEmpty()) tag += " [" + head + "]";
                nearby.add(tag);
            }
            int score = 0;
            if (e instanceof TameableEntity t && p.getUuid().equals(t.getOwnerUuid())) score = 100;
            else if (!cn.isEmpty() && cn.toLowerCase(Locale.ROOT).contains(nick)) score = 90;
            else if (d < 5 && e instanceof LivingEntity && !cn.isEmpty()) score = 50;
            else if (d < 5 && e instanceof ArmorStandEntity && (!head.isEmpty() || !cn.isEmpty())) score = 40;
            if (score == 0) continue;
            if (score > bestScore || (score == bestScore && d < bestDist)) {
                bestScore = score;
                bestDist = d;
                pet = e;
                String nm = cn.replaceAll("(?i)" + Pattern.quote(nickRaw), "")
                        .replaceAll("^[\\s'’:\\-|]+|[\\s'’:\\-|]+$", "");
                if (nm.isEmpty()) nm = cn;
                if (nm.isEmpty()) nm = head;
                if (nm.isEmpty()) nm = e.getType().getName().getString();
                petName = nm;
            }
        }

        return new Info(pieces, set, costumeText, pet, petName, nearby, hp(p, w));
    }

    private static String best(Map<String, Integer> m) {
        String b = null;
        int bc = 0;
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (e.getValue() > bc) { bc = e.getValue(); b = e.getKey(); }
        }
        return b + " " + bc + "/4";
    }

    private static Float parseHp(String raw) {
        String s = strip(raw);
        Matcher m = HP1.matcher(s);
        if (!m.find()) {
            m = HP2.matcher(s);
            if (!m.find()) return null;
        }
        try {
            return Float.parseFloat(m.group(1).replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    /** HP: 1) liczba z nicku/tabu (np. "34❤"), 2) tablica wyników (health), 3) zdrowie z gry. */
    static Hp hp(AbstractClientPlayerEntity p, ClientWorld w) {
        StringBuilder dbg = new StringBuilder("gra " + fmt(p.getHealth()));
        float abs = p.getAbsorptionAmount();
        try {
            Scoreboard sb = w.getScoreboard();
            ScoreHolder holder = ScoreHolder.fromName(p.getGameProfile().getName());

            // wszystkie cele tablicy – do podglądu skąd serwer bierze HP
            for (ScoreboardObjective o : sb.getObjectives()) {
                ReadableScoreboardScore sc = sb.getScore(holder, o);
                if (sc != null) dbg.append(" | ").append(o.getName()).append(' ').append(sc.getScore());
            }

            // 1) liczba w nazwie (prefix/suffix teamu albo nazwa w tabie)
            Float fromName = parseHp(p.getDisplayName().getString());
            MinecraftClient mc = MinecraftClient.getInstance();
            if (fromName == null && mc.getNetworkHandler() != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
                if (e != null && e.getDisplayName() != null) fromName = parseHp(e.getDisplayName().getString());
            }
            if (fromName != null) {
                dbg.append(" | nick ").append(fmt(fromName));
                return new Hp(fromName, abs, "nick", dbg.toString());
            }

            // 2) tablica wyników z hp
            ScoreboardDisplaySlot[] slots = {ScoreboardDisplaySlot.BELOW_NAME, ScoreboardDisplaySlot.LIST};
            for (ScoreboardDisplaySlot slot : slots) {
                ScoreboardObjective o = sb.getObjectiveForSlot(slot);
                if (o == null) continue;
                boolean healthLike = o.getCriterion() == ScoreboardCriterion.HEALTH
                        || o.getRenderType() == ScoreboardCriterion.RenderType.HEARTS;
                if (!healthLike && slot == ScoreboardDisplaySlot.BELOW_NAME) {
                    String dn = strip(o.getDisplayName().getString()).toLowerCase(Locale.ROOT);
                    healthLike = dn.contains("❤") || dn.contains("♥") || dn.contains("hp")
                            || dn.contains("życie") || dn.contains("zdrow");
                }
                if (!healthLike) continue;
                ReadableScoreboardScore sc = sb.getScore(holder, o);
                if (sc != null) {
                    return new Hp(sc.getScore(), abs,
                            slot == ScoreboardDisplaySlot.BELOW_NAME ? "pod nickiem" : "tab", dbg.toString());
                }
            }
        } catch (Exception ignored) {
        }
        return new Hp(p.getHealth(), abs, "gra", dbg.toString());
    }

    private static Piece piece(ItemStack s) {
        if (s.isEmpty()) return new Piece(s, "—", -1, false, "", List.of(), 0, 1, false);

        String name = strip(s.getName().getString());
        int dur = (s.isDamageable() && s.getMaxDamage() > 0)
                ? Math.round(100f * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage()) : -1;

        // zaczarowania (poziomy), bez Wytrzymałości i klątw
        List<E> list = new ArrayList<>();
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : s.getEnchantments().getEnchantmentEntries()) {
            String id = e.getKey().getKey().map(k -> k.getValue().getPath()).orElse("?");
            if (id.equals("unbreaking") || id.endsWith("_curse")) continue;
            list.add(new E(shortName(id), e.getIntValue()));
        }
        list.sort((a, b) -> Integer.compare(b.lvl(), a.lvl()));
        StringBuilder sb = new StringBuilder();
        for (E e : list) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(e.label()).append(' ').append(roman(e.lvl()));
        }

        // prawdziwe % z opisu przedmiotu (lore), np. "Obrażenia: 42%"
        List<String> pct = new ArrayList<>();
        LoreComponent lore = s.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                String t = strip(line.getString());
                if (t.contains("%")) pct.add(t);
            }
        }

        return new Piece(s, name, dur, isCustom(s), sb.toString(), pct,
                s.getCount(), s.getMaxCount(), s.isOf(Items.TOTEM_OF_UNDYING));
    }

    private static boolean isCustom(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.contains(DataComponentTypes.CUSTOM_MODEL_DATA)) return true;
        Identifier m = s.get(DataComponentTypes.ITEM_MODEL);
        return m != null && !m.equals(Registries.ITEM.getId(s.getItem()));
    }

    static String material(String path) {
        return switch (path) {
            case "netherite" -> "Netheryt";
            case "diamond" -> "Diax";
            case "iron" -> "Żelazo";
            case "golden" -> "Złoto";
            case "chainmail" -> "Kolczuga";
            case "turtle" -> "Żółw";
            default -> path.isEmpty() ? "?" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        };
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? r[n] : String.valueOf(n);
    }

    static String shortName(String id) {
        return switch (id) {
            case "sharpness" -> "Ostrość";
            case "smite" -> "Pogromca";
            case "bane_of_arthropods" -> "Stawonogi";
            case "fire_aspect" -> "Ogień";
            case "knockback" -> "Odrzut";
            case "looting" -> "Łupy";
            case "sweeping_edge" -> "Zamach";
            case "mending" -> "Napr";
            case "protection" -> "Ochr";
            case "fire_protection" -> "OchrOgn";
            case "blast_protection" -> "OchrWyb";
            case "projectile_protection" -> "OchrPoc";
            case "feather_falling" -> "Spad";
            case "thorns" -> "Ciernie";
            case "respiration" -> "Odd";
            case "aqua_affinity" -> "Aqua";
            case "depth_strider" -> "Głęb";
            case "frost_walker" -> "Mróz";
            case "soul_speed" -> "Dusze";
            case "swift_sneak" -> "Skrad";
            case "efficiency" -> "Wydaj";
            case "fortune" -> "Fortuna";
            case "silk_touch" -> "Jedwab";
            case "power" -> "Moc";
            case "punch" -> "Uderz";
            case "flame" -> "Płomień";
            case "infinity" -> "Nieskoń";
            case "multishot" -> "Multi";
            case "piercing" -> "Przeb";
            case "quick_charge" -> "Szybk";
            case "density" -> "Gęst";
            case "breach" -> "Wyłom";
            case "wind_burst" -> "Wicher";
            default -> id.isEmpty() ? "?" : Character.toUpperCase(id.charAt(0)) + id.substring(1, Math.min(id.length(), 6));
        };
    }
}
