package pl.szybki.client;

public enum Category {
    CHEATS("cat.cheats"), RADAR("cat.radar"), HELPERS("cat.helpers");

    public final String key;

    Category(String key) { this.key = key; }
}
