package fr.koriamc.krebbs.model;

/**
 * Progression d'un outil, écrite dans l'item lui-même (NBT "KoriaKrebbs").
 */
public final class ToolData {

    /** Niveau atteint, de 0 (neuf) au niveau maximum. */
    public int level;
    /** XP accumulée vers le niveau suivant (0 au niveau maximum). */
    public int xp;
    /** Nombre de lignes de description ajoutées par le plugin à la fin du lore. */
    public int loreLines;

    public ToolData(int level, int xp, int loreLines) {
        this.level = level;
        this.xp = xp;
        this.loreLines = loreLines;
    }

    public static ToolData fresh() {
        return new ToolData(0, 0, 0);
    }
}
