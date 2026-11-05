package fr.koriamc.krebbs.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Objectif à remplir pour atteindre un niveau d'outil (outils.json : &lt;outil&gt;.niveaux.&lt;n&gt;.objectif).
 * <p>
 * Les actions sont signalées avec un type et des clés : par exemple TUER + ZOMBIE,
 * MINER + DIAMOND_ORE / DIAMOND_ORE:0, PECHER + RAW_FISH:3 / COMMUN, VENDRE + ROTTEN_FLESH.
 */
public final class LevelObjective {

    /** Actions reconnues. */
    public static final String KILL = "TUER";
    public static final String MINE = "MINER";
    public static final String HARVEST = "FARMER";
    public static final String FISH = "PECHER";
    public static final String SMELT = "FONDRE";
    public static final String SELL = "VENDRE";

    private static final Set<String> TYPES = new HashSet<String>();

    static {
        Collections.addAll(TYPES, KILL, MINE, HARVEST, FISH, SMELT, SELL);
    }

    private final String type;
    /** Clés acceptées en majuscules (vide = toutes). */
    private final Set<String> targets;
    private final int amount;
    private final String text;

    public LevelObjective(String type, Collection<String> targets, int amount, String text) {
        this.type = type;
        Set<String> set = new HashSet<String>();
        for (String t : targets) {
            String key = t.trim().toUpperCase(Locale.ROOT);
            if (!key.isEmpty() && !key.equals("*")) {
                set.add(key);
            }
        }
        this.targets = Collections.unmodifiableSet(set);
        this.amount = Math.max(1, amount);
        this.text = text;
    }

    /** Type normalisé (TUER, MINER, FARMER, PECHER, FONDRE, VENDRE), ou null s'il est inconnu. */
    public static String parseType(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(Locale.ROOT).replace('É', 'E').replace('È', 'E');
        if (v.equals("CUIRE") || v.equals("CUISSON")) {
            return SMELT;
        }
        if (v.equals("RECOLTER") || v.equals("CULTURES")) {
            return HARVEST;
        }
        if (v.equals("CASSER") || v.equals("BLOCS")) {
            return MINE;
        }
        return TYPES.contains(v) ? v : null;
    }

    public String getType() {
        return type;
    }

    public Set<String> getTargets() {
        return targets;
    }

    public int getAmount() {
        return amount;
    }

    /** Texte affiché ("Tuer 300 zombies"). */
    public String getText() {
        return text;
    }

    /** L'action correspond-elle à cet objectif ? */
    public boolean matches(String action, Collection<String> keys) {
        if (!type.equals(action)) {
            return false;
        }
        if (targets.isEmpty()) {
            return true;
        }
        for (String key : keys) {
            if (key != null && targets.contains(key.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
