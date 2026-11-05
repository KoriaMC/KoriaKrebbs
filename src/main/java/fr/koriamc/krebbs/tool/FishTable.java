package fr.koriamc.krebbs.tool;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Poissons custom pêchés avec la canne du Toucan (config.json : poissons).
 * <p>
 * Chaque rareté (COMMUN, RARE, EPIQUE, LEGENDAIRE) a une chance de remplacer la prise
 * et une liste de poissons. Une rareté n'est tirée que si la canne l'a débloquée
 * (pouvoir POISSONS : 1 = commun ... 4 = légendaire).
 */
public final class FishTable {

    private static final class Tier {
        final String name;
        final int rank;
        final double chance;
        final List<Material> fish = new ArrayList<Material>();

        Tier(String name, int rank, double chance) {
            this.name = name;
            this.rank = rank;
            this.chance = chance;
        }
    }

    private final List<Tier> tiers = new ArrayList<Tier>();
    private final Map<Material, String> tierByFish = new HashMap<Material, String>();
    private final Random random = new Random();

    /** @param problems liste alimentée avec les erreurs de configuration */
    public void load(ConfigurationSection section, List<String> problems) {
        tiers.clear();
        tierByFish.clear();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String name = key.trim().toUpperCase(Locale.ROOT).replace('É', 'E');
            int rank = Powers.FISH_TIERS.indexOf(name);
            ConfigurationSection s = section.getConfigurationSection(key);
            if (rank < 1 || s == null) {
                problems.add("config.json : poissons." + key + " : rareté inconnue (COMMUN, RARE, EPIQUE, LEGENDAIRE).");
                continue;
            }
            Tier tier = new Tier(name, rank, Math.max(0, s.getDouble("chance", 0)));
            for (String fish : s.getStringList("poissons")) {
                Material material = Material.matchMaterial(fish.trim().toUpperCase(Locale.ROOT));
                if (material == null) {
                    problems.add("config.json : poissons." + key + " : poisson inconnu '" + fish
                            + "' (item à ajouter au serveur KoriaMC).");
                    continue;
                }
                tier.fish.add(material);
                tierByFish.put(material, name);
            }
            if (!tier.fish.isEmpty()) {
                tiers.add(tier);
            }
        }
        // les plus rares sont tirés en premier
        java.util.Collections.sort(tiers, new java.util.Comparator<Tier>() {
            @Override
            public int compare(Tier a, Tier b) {
                return b.rank - a.rank;
            }
        });
    }

    /** Rareté d'un poisson custom (COMMUN...), ou null pour un objet vanilla. */
    public String tierOf(ItemStack item) {
        return item == null ? null : tierByFish.get(item.getType());
    }

    /**
     * Tire un poisson custom jusqu'à la rareté {@code maxRank} incluse.
     *
     * @return le poisson, ou null si la prise vanilla est conservée
     */
    public ItemStack roll(int maxRank) {
        for (Tier tier : tiers) {
            if (tier.rank <= maxRank && random.nextDouble() * 100.0 < tier.chance) {
                return new ItemStack(tier.fish.get(random.nextInt(tier.fish.size())));
            }
        }
        return null;
    }
}
