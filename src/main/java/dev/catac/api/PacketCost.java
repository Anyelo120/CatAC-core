package dev.catac.api;

/** Cost class used by CatAC's bounded packet flood budgets. */
public enum PacketCost {
    NORMAL(false),
    HEAVY(true);

    private final boolean heavy;

    PacketCost(boolean heavy) { this.heavy = heavy; }
    public boolean heavy() { return heavy; }
}
