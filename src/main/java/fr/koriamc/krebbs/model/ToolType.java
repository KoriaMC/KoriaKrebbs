package fr.koriamc.krebbs.model;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un outil à niveaux d'outils.json : matériau, façon de gagner de l'XP, paliers et récompenses.
 */
public final class ToolType {

    /** Façon de gagner de l'XP. */
    public enum Source {
        /** Blocs cassés avec l'outil (clé : matériau, ou matériau:data). */
        BLOCS,
        /** Créatures et joueurs tués avec l'outil (clé : type d'entité, PLAYER pour un joueur). */
        MOBS,
        /** Cultures mûres récoltées avec l'outil (clé : matériau du bloc). */
        CULTURES,
        /** Prises à la pêche (clé : matériau de l'objet pêché). */
        PECHE;

        public static Source parse(String value) {
            if (value == null) {
                return null;
            }
            String v = value.trim().toUpperCase(Locale.ROOT).replace('É', 'E').replace('Ê', 'E');
            if (v.equals("BLOCS") || v.equals("BLOCKS") || v.equals("MINAGE")) {
                return BLOCS;
            }
            if (v.equals("MOBS") || v.equals("COMBAT") || v.equals("CREATURES")) {
                return MOBS;
            }
            if (v.equals("CULTURES") || v.equals("RECOLTE") || v.equals("CROPS")) {
                return CULTURES;
            }
            if (v.equals("PECHE") || v.equals("FISHING")) {
                return PECHE;
            }
            return null;
        }
    }

    private final String id;
    private final int materialId;
    private final String materialName;
    private final String icon;
    private final String name;
    private final Source source;
    /** XP par clé (bloc "id" ou "id:data", entité, objet pêché) ; "*" = tout le reste. */
    private final Map<String, Integer> xp;
    private final List<Integer> required;
    private final List<String> howTo;
    private final Map<Integer, LevelReward> rewards;
    /** Objectif de chaque niveau (mode « objectifs » ; vide = mode XP). */
    private final Map<Integer, LevelObjective> objectives;

    public ToolType(String id, int materialId, String materialName, String icon, String name, Source source,
                    Map<String, Integer> xp, List<Integer> required, List<String> howTo, Map<Integer, LevelReward> rewards) {
        this(id, materialId, materialName, icon, name, source, xp, required, howTo, rewards,
                Collections.<Integer, LevelObjective>emptyMap());
    }

    public ToolType(String id, int materialId, String materialName, String icon, String name, Source source,
                    Map<String, Integer> xp, List<Integer> required, List<String> howTo, Map<Integer, LevelReward> rewards,
                    Map<Integer, LevelObjective> objectives) {
        this.objectives = Collections.unmodifiableMap(objectives);
        this.id = id;
        this.materialId = materialId;
        this.materialName = materialName;
        this.icon = icon;
        this.name = name;
        this.source = source;
        this.xp = Collections.unmodifiableMap(xp);
        this.required = Collections.unmodifiableList(required);
        this.howTo = Collections.unmodifiableList(howTo);
        this.rewards = Collections.unmodifiableMap(rewards);
    }

    public String getId() {
        return id;
    }

    public int getMaterialId() {
        return materialId;
    }

    public String getMaterialName() {
        return materialName;
    }

    /** Item affiché dans le menu custom (koriamc:krebbs_pickaxe). */
    public String getIcon() {
        return icon;
    }

    /** Nom affiché (avec codes couleur &amp;). */
    public String getName() {
        return name;
    }

    public Source getSource() {
        return source;
    }

    /** XP pour une clé, ou celle de "*" si la clé n'est pas listée. */
    public int xpFor(String key) {
        Integer value = xp.get(key);
        if (value == null) {
            value = xp.get("*");
        }
        return value == null ? 0 : Math.max(0, value);
    }

    /** XP d'un bloc : "id:data" d'abord, puis "id", puis "*". */
    public int xpForBlock(int blockId, int data) {
        Integer value = xp.get(blockId + ":" + data);
        if (value == null) {
            value = xp.get(String.valueOf(blockId));
        }
        if (value == null) {
            value = xp.get("*");
        }
        return value == null ? 0 : Math.max(0, value);
    }

    /** Un bloc est-il listé explicitement (hors "*") ? */
    public boolean listsBlock(int blockId, int data) {
        return xp.containsKey(blockId + ":" + data) || xp.containsKey(String.valueOf(blockId));
    }

    public int getMaxLevel() {
        return required.size();
    }

    /** XP à accumuler pour passer du niveau {@code level - 1} au niveau {@code level} (1 à max). */
    public int required(int level) {
        if (level < 1 || level > required.size()) {
            return 0;
        }
        return Math.max(1, required.get(level - 1));
    }

    /** Lignes "comment gagner de l'XP" (menu). */
    public List<String> getHowTo() {
        return howTo;
    }

    /** Récompense d'un niveau, ou null. */
    public LevelReward reward(int level) {
        return rewards.get(level);
    }

    public Map<Integer, LevelReward> getRewards() {
        return rewards;
    }

    /** Chaque niveau a son propre objectif (outils du Toucan) au lieu d'un gain d'XP générique. */
    public boolean hasObjectives() {
        return !objectives.isEmpty();
    }

    /** Objectif à remplir pour atteindre {@code level}, ou null. */
    public LevelObjective objective(int level) {
        return objectives.get(level);
    }
}
