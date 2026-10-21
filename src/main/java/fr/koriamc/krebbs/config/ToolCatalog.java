package fr.koriamc.krebbs.config;

import fr.koriamc.krebbs.model.LevelReward;
import fr.koriamc.krebbs.model.ToolType;
import fr.koriamc.krebbs.util.Items;
import fr.koriamc.krebbs.util.MaterialSet;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Les outils à niveaux d'outils.json, dans l'ordre du fichier.
 */
@SuppressWarnings("deprecation")
public final class ToolCatalog {

    /** Noms français acceptés pour les enchantements (en plus des noms Bukkit). */
    private static final Map<String, String> ENCHANT_ALIASES = new HashMap<String, String>();

    static {
        ENCHANT_ALIASES.put("EFFICACITE", "DIG_SPEED");
        ENCHANT_ALIASES.put("SOLIDITE", "DURABILITY");
        ENCHANT_ALIASES.put("FORTUNE", "LOOT_BONUS_BLOCKS");
        ENCHANT_ALIASES.put("TOUCHER_DE_SOIE", "SILK_TOUCH");
        ENCHANT_ALIASES.put("TRANCHANT", "DAMAGE_ALL");
        ENCHANT_ALIASES.put("CHATIMENT", "DAMAGE_UNDEAD");
        ENCHANT_ALIASES.put("FLEAU_DES_ARTHROPODES", "DAMAGE_ARTHROPODS");
        ENCHANT_ALIASES.put("BUTIN", "LOOT_BONUS_MOBS");
        ENCHANT_ALIASES.put("AURA_DE_FEU", "FIRE_ASPECT");
        ENCHANT_ALIASES.put("RECUL", "KNOCKBACK");
        ENCHANT_ALIASES.put("APPAT", "LURE");
        ENCHANT_ALIASES.put("CHANCE_DE_LA_MER", "LUCK");
    }

    private final Map<String, ToolType> tools = new LinkedHashMap<String, ToolType>();
    private final Map<Integer, ToolType> byMaterial = new HashMap<Integer, ToolType>();
    private final List<String> problems = new ArrayList<String>();

    /**
     * @param file     outils.json, sans valeurs par défaut (une option retirée reste retirée)
     * @param required paliers d'XP de config.json, utilisés quand l'outil n'a pas les siens
     */
    public void load(Configuration file, List<Integer> required) {
        tools.clear();
        byMaterial.clear();
        problems.clear();
        for (String id : file.getKeys(false)) {
            ConfigurationSection s = file.getConfigurationSection(id);
            if (s == null) {
                problems.add("outils.json : " + id + " doit être un bloc { ... }");
                continue;
            }
            if (!s.getBoolean("actif", true)) {
                continue;
            }
            ToolType tool = read(id, s, required);
            if (tool == null) {
                continue;
            }
            ToolType other = byMaterial.get(tool.getMaterialId());
            if (other != null) {
                problems.add("outils.json : " + id + " utilise le même matériau que " + other.getId() + ", ignoré.");
                continue;
            }
            tools.put(id.toLowerCase(Locale.ROOT), tool);
            byMaterial.put(tool.getMaterialId(), tool);
        }
    }

    private ToolType read(String id, ConfigurationSection s, List<Integer> globalRequired) {
        String where = "outils.json : " + id;
        String materialSpec = s.getString("materiau", "");
        int[] parsed = MaterialSet.parse(materialSpec);
        if (parsed == null) {
            problems.add(where + ".materiau inconnu (" + materialSpec + "). Le serveur KoriaMC est-il à jour ?");
            return null;
        }
        ToolType.Source source = ToolType.Source.parse(s.getString("type-xp"));
        if (source == null) {
            problems.add(where + ".type-xp invalide (" + s.getString("type-xp") + ") : BLOCS, MOBS, CULTURES ou PECHE.");
            return null;
        }
        Material material = Material.getMaterial(parsed[0]);
        String materialName = material == null ? materialSpec : material.name();
        String icon = s.getString("icone", "");
        if (icon.isEmpty()) {
            icon = "koriamc:" + materialName.toLowerCase(Locale.ROOT);
        }

        Map<String, Integer> xp = new HashMap<String, Integer>();
        ConfigurationSection xpSection = s.getConfigurationSection("xp");
        if (xpSection != null) {
            for (String key : xpSection.getKeys(false)) {
                Integer amount = Settings.toInt(xpSection.get(key));
                if (amount == null) {
                    problems.add(where + ".xp." + key + " doit être un nombre.");
                    continue;
                }
                String resolved = key(source, key, where);
                if (resolved != null) {
                    xp.put(resolved, amount);
                }
            }
        }

        List<Integer> required = globalRequired;
        if (s.isList("xp-requise")) {
            required = new ArrayList<Integer>();
            for (Object value : s.getList("xp-requise")) {
                Integer amount = Settings.toInt(value);
                if (amount != null && amount > 0) {
                    required.add(amount);
                }
            }
            if (required.isEmpty()) {
                problems.add(where + ".xp-requise est vide, paliers de config.json utilisés.");
                required = globalRequired;
            }
        }

        Map<Integer, LevelReward> rewards = new TreeMap<Integer, LevelReward>();
        ConfigurationSection rewardSection = s.getConfigurationSection("recompenses");
        if (rewardSection != null) {
            for (String key : rewardSection.getKeys(false)) {
                Integer level = Settings.toInt(key);
                ConfigurationSection r = rewardSection.getConfigurationSection(key);
                if (level == null || level < 1 || level > required.size() || r == null) {
                    problems.add(where + ".recompenses." + key + " : niveau invalide (1 à " + required.size() + ").");
                    continue;
                }
                rewards.put(level, reward(level, r, where + ".recompenses." + key));
            }
        }

        return new ToolType(id.toLowerCase(Locale.ROOT), parsed[0], materialName, icon, s.getString("nom", id), source,
                xp, required, Items.stringList(s, "comment"), rewards);
    }

    /** Clé d'XP normalisée : id ou id:data pour un bloc, nom Bukkit pour une entité ou un objet. */
    private String key(ToolType.Source source, String key, String where) {
        if (key.equals("*")) {
            return key;
        }
        if (source == ToolType.Source.MOBS) {
            String name = key.trim().toUpperCase(Locale.ROOT);
            if (!name.equals("PLAYER") && EntityType.fromName(key) == null && !isEntityType(name)) {
                problems.add(where + ".xp." + key + " : type d'entité inconnu.");
                return null;
            }
            return name;
        }
        int[] parsed = MaterialSet.parse(key);
        if (parsed == null) {
            problems.add(where + ".xp." + key + " : matériau inconnu.");
            return null;
        }
        if (source == ToolType.Source.PECHE) {
            Material material = Material.getMaterial(parsed[0]);
            return material == null ? String.valueOf(parsed[0]) : material.name();
        }
        return parsed[1] < 0 ? String.valueOf(parsed[0]) : parsed[0] + ":" + parsed[1];
    }

    private static boolean isEntityType(String name) {
        try {
            EntityType.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private LevelReward reward(int level, ConfigurationSection r, String where) {
        Map<Enchantment, Integer> enchants = new LinkedHashMap<Enchantment, Integer>();
        ConfigurationSection e = r.getConfigurationSection("enchantements");
        if (e != null) {
            for (String name : e.getKeys(false)) {
                Enchantment enchantment = enchantment(name);
                Integer value = Settings.toInt(e.get(name));
                if (enchantment == null) {
                    problems.add(where + ".enchantements." + name + " : enchantement inconnu.");
                } else if (value == null || value < 1) {
                    problems.add(where + ".enchantements." + name + " doit être un niveau (1, 2, 3...).");
                } else {
                    enchants.put(enchantment, value);
                }
            }
        }
        return new LevelReward(level, enchants, Math.max(0, r.getDouble("argent", 0)), Items.stringList(r, "commandes"),
                Items.stringList(r, "message"), Items.stringList(r, "description"), r.getBoolean("annonce", false));
    }

    /** Nom Bukkit (DIG_SPEED) ou français (efficacite, chance-de-la-mer). */
    public static Enchantment enchantment(String name) {
        String key = name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')
                .replace('É', 'E').replace('È', 'E').replace('Ê', 'E').replace('Â', 'A').replace('À', 'A');
        String alias = ENCHANT_ALIASES.get(key);
        return Enchantment.getByName(alias != null ? alias : key);
    }

    /** L'outil correspondant à cet objet, ou null. */
    public ToolType of(ItemStack item) {
        return item == null ? null : byMaterial.get(item.getTypeId());
    }

    public ToolType get(String id) {
        return id == null ? null : tools.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<ToolType> all() {
        return tools.values();
    }

    public List<String> ids() {
        return new ArrayList<String>(tools.keySet());
    }

    public int count() {
        return tools.size();
    }

    public List<String> getProblems() {
        return problems;
    }
}
