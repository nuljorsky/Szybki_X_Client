package pl.szybki.client;

public abstract class Setting {
    public final String key, nameKey;

    protected Setting(String key, String nameKey) { this.key = key; this.nameKey = nameKey; }

    public static final class Bool extends Setting {
        public boolean v;
        public Bool(String k, String n, boolean v) { super(k, n); this.v = v; }
    }

    public static final class Slider extends Setting {
        public double v;
        public final double min, max, step;
        public Slider(String k, String n, double min, double max, double step, double v) {
            super(k, n); this.min = min; this.max = max; this.step = step; this.v = v;
        }
        public void setFromRatio(double r) {
            r = Math.max(0, Math.min(1, r));
            double x = min + (max - min) * r;
            v = Math.max(min, Math.min(max, Math.round(x / step) * step));
        }
        public double ratio() { return (v - min) / (max - min); }
    }

    /** opcje to klucze tłumaczeń albo zwykłe teksty (gdy brak klucza, wyświetlany jest tekst) */
    public static final class Choice extends Setting {
        public int i;
        public final String[] opts;
        public Choice(String k, String n, int i, String... opts) { super(k, n); this.i = i; this.opts = opts; }
        public String get() { return opts[Math.max(0, Math.min(opts.length - 1, i))]; }
        public void next() { i = (i + 1) % opts.length; }
    }

    public static final class Button extends Setting {
        public final Runnable run;
        public Button(String k, String n, Runnable run) { super(k, n); this.run = run; }
    }
}
