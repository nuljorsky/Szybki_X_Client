package pl.szybki.client;

/** Motyw GUI: ciemny/jasny oraz animowany/matowy. */
public final class Theme {
    public static boolean dark = true;
    public static boolean anim = true;

    public static int panel()  { return dark ? 0x0E1117 : 0xF7F9FC; }
    public static int panel2() { return dark ? 0x1A1F2B : 0xE4E9F2; }
    public static int line()   { return dark ? 0x2A3242 : 0xC3CBDA; }
    public static int text()   { return dark ? 0xE8ECF4 : 0x141A26; }
    public static int sub()    { return dark ? 0x8A93A6 : 0x5A6478; }
    public static int a1()     { return dark ? 0x00E5FF : 0x0A74E8; }
    public static int a2()     { return dark ? 0x8A5CFF : 0x7C3AED; }
    public static int good()   { return dark ? 0x00E676 : 0x0E9F57; }
    public static int warn()   { return dark ? 0xFFD54F : 0xC98A00; }
    public static int bad()    { return dark ? 0xFF5252 : 0xD93030; }
    public static int mapBg()  { return dark ? 0x0A0C11 : 0xD5DCE8; }
    public static int dim()    { return dark ? 0x000000 : 0x1C2430; }
}
