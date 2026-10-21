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

    /** Ouvre le menu de l'outil tenu en main. */
    public static void openMenu(Player player) {
        plugin().menus().open(player, "principal");
    }
}
