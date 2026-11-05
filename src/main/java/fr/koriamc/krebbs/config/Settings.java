package fr.koriamc.krebbs.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Réglages typés de config.json, relus à chaque rechargement.
 */
public final class Settings {

    public boolean debug;

    // Niveaux
    /** XP à accumuler pour chaque niveau (le nombre d'éléments est le niveau maximum). */
    public List<Integer> required = new ArrayList<Integer>();

    // Menu
    public boolean shiftRightClick;
    /** Identifiant du layout du menu custom (koriamc/guis/&lt;id&gt;.json). */
    public String layout;
    public boolean installLayout;

    // Affichage
    public boolean actionBar;
    public boolean lore;
    public int barLength;
    public String barChar;
    public String barFull;
    public String barEmpty;
    public boolean announceMax;

    // Anti-abus
    public boolean ignorePlaced;
    public double spawnerMultiplier;
    public int sameVictimSeconds;
    public boolean creative;

    // Pouvoirs du Toucan (config.json : pouvoirs)
    /** Minerais modés (pouvoir DUPLICATION_MODE). */
    public java.util.Set<String> moddedOres = new java.util.HashSet<String>();
    /** Cultures modées : matériau du bloc -> data de la culture mûre. */
    public Map<String, Integer> moddedCrops = new java.util.HashMap<String, Integer>();
    /** Objet donné par le pouvoir PETALES. */
    public String petalItem;
    /** Durée de l'effet du pouvoir COMBO, en ticks. */
    public int comboTicks;
    /** Télékinésie : ce qui ne rentre pas dans l'inventaire tombe au sol (sinon il est perdu). */
    public boolean telekinesisFullDrop;

    // Titres
    public int titleFadeIn;
    public int titleStay;
    public int titleFadeOut;

    private final Map<String, String> sounds = new HashMap<String, String>();

    public void load(ConfigurationSection c, Logger logger) {
        debug = c.getBoolean("debug");
        required = new ArrayList<Integer>();
        for (Object value : c.getList("niveaux.xp-requise", new ArrayList<Object>())) {
            Integer amount = toInt(value);
            if (amount == null || amount < 1) {
                logger.warning("config.json : niveaux.xp-requise contient une valeur invalide (" + value + "), ignorée.");
                continue;
            }
            required.add(amount);
        }
        if (required.isEmpty()) {
            logger.warning("config.json : niveaux.xp-requise est vide, 20 niveaux de 1000 XP utilisés.");
            for (int i = 0; i < 20; i++) {
                required.add(1000);
            }
        }
        shiftRightClick = c.getBoolean("menu.shift-clic-droit", true);
        layout = str(c, "menu.layout", "krebbs").trim().toLowerCase();
        installLayout = c.getBoolean("menu.installer-layout", true);
        actionBar = c.getBoolean("affichage.barre-action", true);
        lore = c.getBoolean("affichage.lore", true);
        barLength = Math.max(5, Math.min(60, c.getInt("affichage.barre.longueur", 20)));
        barChar = str(c, "affichage.barre.caractere", "|");
        barFull = str(c, "affichage.barre.couleur-pleine", "&a");
        barEmpty = str(c, "affichage.barre.couleur-vide", "&8");
        announceMax = c.getBoolean("affichage.annonce-niveau-max", true);
        ignorePlaced = c.getBoolean("anti-abus.ignorer-blocs-poses", true);
        spawnerMultiplier = Math.max(0, c.getDouble("anti-abus.multiplicateur-mobs-spawner", 0.25));
        sameVictimSeconds = Math.max(0, c.getInt("anti-abus.delai-meme-victime-secondes", 600));
        creative = c.getBoolean("anti-abus.xp-en-creatif", false);
        moddedOres.clear();
        for (String ore : c.getStringList("pouvoirs.minerais-modes")) {
            moddedOres.add(ore.trim().toUpperCase(java.util.Locale.ROOT));
        }
        moddedCrops.clear();
        ConfigurationSection crops = c.getConfigurationSection("pouvoirs.cultures-modees");
        if (crops != null) {
            for (String crop : crops.getKeys(false)) {
                moddedCrops.put(crop.trim().toUpperCase(java.util.Locale.ROOT), crops.getInt(crop, 7));
            }
        }
        petalItem = c.getString("pouvoirs.objet-petale", "PETALE_TROPICAL");
        comboTicks = Math.max(20, c.getInt("pouvoirs.combo-duree-ticks", 60));
        telekinesisFullDrop = c.getBoolean("pouvoirs.telekinesie-inventaire-plein-au-sol", true);
        titleFadeIn = c.getInt("titres.apparition", 5);
        titleStay = c.getInt("titres.duree", 40);
        titleFadeOut = c.getInt("titres.disparition", 10);
        sounds.clear();
        ConfigurationSection soundSection = c.getConfigurationSection("sons");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, soundSection.getString(key, ""));
            }
        }
    }

    /** Son de la section sons (chaîne vide si absent). */
    public String sound(String key) {
        String value = sounds.get(key);
        return value == null ? "" : value;
    }

    static Integer toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(ConfigurationSection c, String path, String def) {
        String value = c.getString(path);
        return value == null ? def : value;
    }
}
