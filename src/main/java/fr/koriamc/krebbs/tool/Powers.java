package fr.koriamc.krebbs.tool;

import fr.koriamc.krebbs.model.LevelReward;
import fr.koriamc.krebbs.model.ToolData;
import fr.koriamc.krebbs.model.ToolType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pouvoirs que les niveaux des outils du Toucan peuvent débloquer (outils.json :
 * niveaux.&lt;n&gt;.pouvoirs). Un pouvoir débloqué reste actif ; si plusieurs niveaux donnent
 * le même pouvoir, la plus grande valeur l'emporte.
 */
public final class Powers {

    // Épée
    /** Les mobs (hors joueurs) meurent en N coups. Koalas et Endermans exclus sans ONESHOT_*. */
    public static final String MOB_HITS = "COUPS_MOBS";
    public static final String ONESHOT_KOALA = "ONESHOT_KOALA";
    public static final String ONESHOT_ENDERMAN = "ONESHOT_ENDERMAN";
    /** $ gagnés par mob tué. */
    public static final String MOB_MONEY = "ARGENT_MOB";
    /** Tue jusqu'à N mobs du même type d'un coup (2 = double kill, 3 = triple kill). */
    public static final String MULTI_KILL = "MULTI_KILL";

    // Pioche
    /** Points d'expérience en plus par bloc miné. */
    public static final String MINE_XP = "XP_MINAGE";
    /** Célérité pendant le minage (valeur = niveau de l'effet). */
    public static final String COMBO = "COMBO";
    /** Mine une zone : 2 = 1x2 (le bloc du dessous en plus). */
    public static final String MINE_AREA = "MINAGE_ZONE";
    /** $ gagnés par bloc miné. */
    public static final String BLOCK_MONEY = "ARGENT_BLOC";
    /** % de chance de doubler les drops des minerais modés. */
    public static final String DUPLICATE_ORES = "DUPLICATION_MODE";

    // Houe
    /** Drops bonus sur les cultures vanilla (0 à N en plus). */
    public static final String CROP_FORTUNE = "FORTUNE_CULTURES";
    /** Drops bonus sur les cultures modées (0 à N en plus). */
    public static final String MODDED_CROP_FORTUNE = "FORTUNE_CULTURES_MODE";
    /** Laboure une zone : 2 = 1x2, 3 = 3x3. */
    public static final String TILL_AREA = "LABOUR";
    /** Récolte une zone : 2 = 1x2, 3 = 3x3. */
    public static final String HARVEST_AREA = "RECOLTE_ZONE";
    /** Replante automatiquement les cultures récoltées (avec une graine de l'inventaire). */
    public static final String REPLANT = "REPLANTATION";
    /** Les effets de la houe s'appliquent aussi aux cultures modées. */
    public static final String MODDED_CROPS = "CULTURES_MODE";
    /** % de chance de récolter un pétale tropical. */
    public static final String PETALS = "PETALES";

    // Canne
    /** % de chance de doubler le poisson pêché. */
    public static final String DOUBLE_FISH = "DOUBLE_POISSON";
    /** Rareté maximale des poissons custom : 1 commun, 2 rare, 3 épique, 4 légendaire. */
    public static final String FISH_TIER = "POISSONS";

    // Commun
    /** Les drops (blocs, mobs, poissons) vont directement dans l'inventaire. */
    public static final String TELEKINESIS = "TELEKINESIE";

    private static final Set<String> KNOWN = new LinkedHashSet<String>(Arrays.asList(
            MOB_HITS, ONESHOT_KOALA, ONESHOT_ENDERMAN, MOB_MONEY, MULTI_KILL,
            MINE_XP, COMBO, MINE_AREA, BLOCK_MONEY, DUPLICATE_ORES,
            CROP_FORTUNE, MODDED_CROP_FORTUNE, TILL_AREA, HARVEST_AREA, REPLANT, MODDED_CROPS, PETALS,
            DOUBLE_FISH, FISH_TIER, TELEKINESIS));

    /** Noms de rareté de poisson (index = valeur de POISSONS). */
    public static final List<String> FISH_TIERS = Collections.unmodifiableList(
            Arrays.asList("VANILLA", "COMMUN", "RARE", "EPIQUE", "LEGENDAIRE"));

    private Powers() {
    }

    public static boolean isKnown(String power) {
        return KNOWN.contains(power);
    }

    public static String list() {
        StringBuilder sb = new StringBuilder();
        for (String power : KNOWN) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(power);
        }
        return sb.toString();
    }

    /** Pouvoirs actifs d'un outil : ceux de tous les niveaux atteints. */
    public static Map<String, Double> of(ToolType type, ToolData data) {
        Map<String, Double> out = new HashMap<String, Double>();
        if (type == null) {
            return out;
        }
        for (int level = 1; level <= data.level; level++) {
            LevelReward reward = type.reward(level);
            if (reward == null) {
                continue;
            }
            for (Map.Entry<String, Double> e : reward.getPowers().entrySet()) {
                Double current = out.get(e.getKey());
                if (current == null || e.getValue() > current) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        }
        return out;
    }

    /** Valeur d'un pouvoir (0 s'il n'est pas débloqué). */
    public static double value(Map<String, Double> powers, String power) {
        Double v = powers.get(power);
        return v == null ? 0 : v;
    }
}
