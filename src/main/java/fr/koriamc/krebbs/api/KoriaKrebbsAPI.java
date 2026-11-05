package fr.koriamc.krebbs.api;

import fr.koriamc.krebbs.KoriaKrebbs;
import fr.koriamc.krebbs.model.ToolType;
import fr.koriamc.krebbs.tool.ItemData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Accès aux outils Krebbs pour les autres plugins (quêtes, boutiques...).
 * Évènements : fr.koriamc.krebbs.api.event.
 */
public final class KoriaKrebbsAPI {

    private KoriaKrebbsAPI() {
    }

    private static KoriaKrebbs plugin() {
        KoriaKrebbs plugin = KoriaKrebbs.get();
        if (plugin == null) {
            throw new IllegalStateException("KoriaKrebbs n'est pas activé");
        }
        return plugin;
    }

    /** L'objet est-il un outil à niveaux ? */
    public static boolean isTool(ItemStack item) {
        return plugin().tools().of(item) != null;
    }

    /** Identifiant de l'outil (pickaxe, sword...), ou null. */
    public static String getToolId(ItemStack item) {
        ToolType type = plugin().tools().of(item);
        return type == null ? null : type.getId();
    }

    /** Niveau de l'outil (0 si neuf ou si ce n'est pas un outil). */
    public static int getLevel(ItemStack item) {
        return plugin().tools().of(item) == null ? 0 : ItemData.read(item).level;
    }

    /** XP accumulée vers le niveau suivant. */
    public static int getXp(ItemStack item) {
        return plugin().tools().of(item) == null ? 0 : ItemData.read(item).xp;
    }

    /**
     * Ajoute de l'XP à l'outil tenu en main (récompenses comprises).
     *
     * @return niveaux gagnés, ou -1 si le joueur ne tient pas d'outil
     */
    public static int addXp(Player player, int amount) {
        return plugin().toolManager().addXp(player, player.getInventory().getHeldItemSlot(), amount, true);
    }

    /**
     * Signale une vente au shop : fait progresser les outils du Toucan de l'inventaire dont
     * l'objectif du prochain niveau est « VENDRE » cet objet.
     *
     * @param sold   objet vendu (type et data)
     * @param amount quantité vendue
     */
    public static void progressSale(Player player, ItemStack sold, int amount) {
        java.util.List<String> keys = new java.util.ArrayList<String>();
        keys.add(sold.getType().name());
        keys.add(sold.getType().name() + ":" + sold.getDurability());
        plugin().toolManager().progress(player, fr.koriamc.krebbs.model.LevelObjective.SELL, keys, amount, false);
    }

    /**
     * Valeur d'un pouvoir de l'objet (0 s'il n'est pas débloqué) : TELEKINESIE, POISSONS...
     * Voir fr.koriamc.krebbs.tool.Powers.
     */
    public static double getPower(ItemStack item, String power) {
        return fr.koriamc.krebbs.tool.Powers.value(plugin().toolManager().powers(item), power);
    }

    /** Ouvre le menu de l'outil tenu en main. */
    public static void openMenu(Player player) {
        plugin().menus().open(player, "principal");
    }
}
