package pl.szybki.client;

import java.util.HashMap;
import java.util.Map;

/** Języki: 0 = polski (domyślny), 1 = angielski, 2 = ukraiński. */
public final class Lang {
    public static int cur = 0;
    public static final String[] NAMES = {"Polski", "English", "Українська (Nager1406)"};
    private static final Map<String, String[]> M = new HashMap<>();

    private static void p(String k, String pl, String en, String uk) { M.put(k, new String[]{pl, en, uk}); }

    public static String t(String k) {
        String[] v = M.get(k);
        return v == null ? k : v[cur];
    }

    static {
        // interfejs
        p("ui.hint", "Klik – włącz/wyłącz  •  Prawy klik – ustawienia  •  Kółko – przewijanie  •  RShift – zamknij",
                "Click – toggle  •  Right click – settings  •  Wheel – scroll  •  RShift – close",
                "Клік – увімк./вимк.  •  ПКМ – налаштування  •  Колесо – прокрутка  •  RShift – закрити");
        p("ui.language", "Język", "Language", "Мова");
        p("ui.settings", "Ustawienia", "Settings", "Налаштування");
        p("ui.nosettings", "Ten moduł nie ma ustawień.", "This module has no settings.", "Цей модуль не має налаштувань.");
        p("ui.close", "Zamknij", "Close", "Закрити");
        p("ui.empty", "Brak modułów w tej kategorii", "No modules in this category", "У цій категорії немає модулів");

        // kategorie
        p("cat.cheats", "Szybkie - CHEATY", "Fast - CHEATS", "Швидкі - ЧИТИ");
        p("cat.radar", "Szybki - Radar", "Fast - Radar", "Швидкий - Радар");
        p("cat.helpers", "Szybkie - Wspomagacze", "Fast - Helpers", "Швидкі - Помічники");

        // opisy modułów
        p("d.Hitboxes", "Bijesz graczy przez ściany (nie bije sam).", "Hit players through walls (no auto-attack).", "Бий гравців крізь стіни (без авто-атаки).");
        p("d.Freecam", "Wolna kamera – latasz kamerą, postać stoi.", "Free camera – fly around while your player stays.", "Вільна камера – літаєш камерою, персонаж стоїть.");
        p("d.FastPlace", "Stawiaj bloki bez opóźnienia.", "Place blocks without delay.", "Став блоки без затримки.");
        p("d.AutoLeave", "Wychodzi z serwera, gdy ktoś podejdzie lub masz mało HP.", "Leaves the server when someone gets close or HP is low.", "Виходить із сервера, коли хтось підійде або мало HP.");
        p("d.Radar", "Mapa z góry, lista graczy, ekwipunek, set, pet i TPA.", "Top-down map, players, gear, set, pet and TPA.", "Карта зверху, гравці, спорядження, сет, пет і TPA.");
        p("d.Tracers", "Linie do graczy w zasięgu (domyślnie 100 m).", "Lines to players in range (100 m by default).", "Лінії до гравців у радіусі (100 м за замовчуванням).");
        p("d.Nametags", "Nametagi PRO: nick, HP, dystans i ekwipunek.", "PRO nametags: name, HP, distance and gear.", "PRO неймтеги: нік, HP, відстань і спорядження.");
        p("d.BlockESP", "Skrzynie, spawnery i rudy widoczne przez ściany.", "Chests, spawners and ores through walls.", "Скрині, спавнери та руди крізь стіни.");
        p("d.ElytraDetector", "Czerwony alarm, gdy ktoś leci elytrą w zasięgu.", "Red alert when someone flies with an elytra nearby.", "Червона тривога, коли хтось летить на елітрах поруч.");
        p("d.AntyKostka", "Zapisz ekwipunek i przywróć go, gdy ktoś Ci go pomiesza.", "Save your inventory and restore it if it gets shuffled.", "Збережи інвентар і відновлюй, якщо його перемішали.");
        p("d.AutoNielota", "Gdy ktoś obok zakłada elytrę, bierze wędkę Nielot do lewej ręki.", "When someone nearby equips an elytra, swaps the Nielot rod to your offhand.", "Коли хтось поруч одягає елітри, бере вудку Nielot у ліву руку.");
        p("d.NameProtect", "Ukrywa Twój nick w czacie, tabie i na ekranie.", "Hides your name in chat, tab and on screen.", "Ховає твій нік у чаті, табі та на екрані.");
        p("d.MiddleClickFriend", "Środkowy przycisk na graczu dodaje/usuwa znajomego.", "Middle click on a player adds/removes a friend.", "Середня кнопка на гравці додає/видаляє друга.");
        p("d.ArrayList", "Lista włączonych modułów na ekranie.", "On-screen list of enabled modules.", "Список увімкнених модулів на екрані.");

        // nazwy ustawień
        p("set.reach", "Zasięg", "Reach", "Дальність");
        p("set.playersOnly", "Tylko gracze", "Players only", "Лише гравці");
        p("set.speed", "Prędkość", "Speed", "Швидкість");
        p("set.blocksOnly", "Tylko bloki", "Blocks only", "Лише блоки");
        p("set.leaveDist", "Dystans gracza", "Player distance", "Відстань гравця");
        p("set.leaveHp", "Wyjdź gdy HP ≤ (0 = wył.)", "Leave when HP ≤ (0 = off)", "Вийти при HP ≤ (0 = вимк.)");
        p("set.ignoreFriends", "Ignoruj znajomych", "Ignore friends", "Ігнорувати друзів");
        p("set.range", "Zasięg", "Range", "Радіус");
        p("set.thick", "Grubość linii", "Line thickness", "Товщина ліній");
        p("set.from", "Start linii", "Line start", "Початок ліній");
        p("opt.bottom", "Dół ekranu", "Screen bottom", "Низ екрана");
        p("opt.center", "Środek (celownik)", "Center (crosshair)", "Центр (приціл)");
        p("set.showDist", "Pokaż dystans", "Show distance", "Показувати відстань");
        p("set.scale", "Skala", "Scale", "Масштаб");
        p("set.showHp", "Pokaż HP", "Show HP", "Показувати HP");
        p("set.showGear", "Pokaż ekwipunek", "Show gear", "Показувати спорядження");
        p("set.hideVanilla", "Ukryj zwykłe nametagi", "Hide vanilla nametags", "Ховати стандартні неймтеги");
        p("set.radius", "Promień", "Radius", "Радіус");
        p("set.storage", "Skrzynie i shulkery", "Chests and shulkers", "Скрині та шалкери");
        p("set.spawners", "Spawnery", "Spawners", "Спавнери");
        p("set.ores", "Rudy", "Ores", "Руди");
        p("set.nielotDist", "Dystans gracza", "Player distance", "Відстань гравця");
        p("set.fakeName", "Fałszywy nick", "Fake name", "Фальшивий нік");
        p("btn.save", "Zapisz ekwipunek", "Save inventory", "Зберегти інвентар");
        p("btn.restore", "Przywróć ekwipunek", "Restore inventory", "Відновити інвентар");
        p("btn.openRadar", "Otwórz radar", "Open radar", "Відкрити радар");

        // komunikaty
        p("msg.saved", "Ekwipunek zapisany", "Inventory saved", "Інвентар збережено");
        p("msg.restoring", "Przywracam ekwipunek...", "Restoring inventory...", "Відновлюю інвентар...");
        p("msg.nosaved", "Brak zapisanego ekwipunku", "No saved inventory", "Немає збереженого інвентарю");
        p("msg.already", "Ekwipunek już jest taki jak zapisany", "Inventory already matches the saved one", "Інвентар вже збігається зі збереженим");
        p("msg.friendAdd", "Dodano znajomego: ", "Friend added: ", "Друга додано: ");
        p("msg.friendRemove", "Usunięto znajomego: ", "Friend removed: ", "Друга видалено: ");
        p("msg.nielotNone", "Brak wędki Nielot w ekwipunku!", "No Nielot rod in inventory!", "Немає вудки Nielot в інвентарі!");
        p("alert.elytra", "UWAŻAJ WLOTA!!!", "WATCH OUT, ELYTRA!!!", "ОБЕРЕЖНО, ЕЛІТРИ!!!");
    }
}
