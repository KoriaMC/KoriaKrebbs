package fr.koriamc.krebbs.gui;

import fr.koriamc.krebbs.KoriaKrebbs;
import fr.koriamc.krebbs.hook.GuiHook;
import fr.koriamc.krebbs.model.ToolData;
import fr.koriamc.krebbs.model.ToolType;
import fr.koriamc.krebbs.tool.ItemData;
import fr.koriamc.krebbs.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu custom de l'outil tenu en main (layout KoriaMC, koriamc/guis/krebbs.json) : niveau,
 * barre d'XP, comment gagner de l'XP et récompenses des niveaux.
 *
 * Le plugin n'envoie que des données : variables %var_krebbs_...% et listes
 * list:krebbs_comment / list:krebbs_recompenses. L'apparence se modifie dans l'éditeur
 * de GUI KoriaMC sans toucher au plugin.
 */
public final class ToolMenu {

    /** Page du layout (conteneur PAGES "vues") : 1 = outil, 2 = récompenses. */
    public static final int PAGE_TOOL = 1;
    public static final int PAGE_REWARDS = 2;

    private final KoriaKrebbs plugin;

    public ToolMenu(KoriaKrebbs plugin) {
        this.plugin = plugin;
    }

    /** @return false si le joueur ne tient pas d'outil Krebbs */
    public boolean open(Player player, int page) {
        ItemStack item = player.getItemInHand();
        ToolType type = plugin.tools().of(item);
        if (type == null) {
            plugin.msg().send(player, "krebbs.not-holding");
            return false;
        }
        ToolData data = ItemData.read(item);
        GuiHook gui = plugin.gui();
        if (!gui.canDisplay(player)) {
            chat(player, type, data, page);
            return true;
        }
        push(player, type, data);
        String layout = plugin.settings().layout;
        if (!gui.open(player, layout)) {
            plugin.getLogger().warning("Le menu " + layout + " n'a pas pu s'ouvrir : le fichier koriamc/guis/" + layout
                    + ".json existe-t-il ?");
            chat(player, type, data, page);
            return true;
        }
        if (page != PAGE_TOOL) {
            gui.clientActions(player, layout, "page: vues " + page);
        }
        return true;
    }

    /** Variables et listes lues par le layout. */
    private void push(Player player, ToolType type, ToolData data) {
        GuiHook gui = plugin.gui();
        int max = type.getMaxLevel();
        boolean maxed = data.level >= max;
        int required = maxed ? 0 : type.required(data.level + 1);
        int percent = maxed ? 100 : (int) Math.floor(data.xp * 100.0 / Math.max(1, required));
        gui.setVariable(player, "krebbs_outil", Text.color(type.getName()));
        gui.setVariable(player, "krebbs_icone", type.getIcon());
        gui.setVariable(player, "krebbs_niveau", String.valueOf(data.level));
        gui.setVariable(player, "krebbs_max", String.valueOf(max));
        gui.setVariable(player, "krebbs_xp", String.valueOf(maxed ? 1 : data.xp));
        gui.setVariable(player, "krebbs_requis", String.valueOf(maxed ? 1 : required));
        gui.setVariable(player, "krebbs_restant", String.valueOf(Math.max(0, required - data.xp)));
        gui.setVariable(player, "krebbs_pourcentage", String.valueOf(percent));
        // avec le signe : "%var_x%%" serait mal découpé en placeholders
        gui.setVariable(player, "krebbs_pourcent", percent + "%");
        gui.setVariable(player, "krebbs_max_atteint", String.valueOf(maxed));
        gui.setVariable(player, "krebbs_prochain", String.valueOf(Math.min(max, data.level + 1)));
        gui.setVariable(player, "krebbs_prochaine", maxed ? "" : join(plugin.toolManager().describe(type.reward(data.level + 1)), ", "));

        List<Map<String, String>> howTo = new ArrayList<Map<String, String>>();
        for (String line : type.getHowTo()) {
            howTo.add(entry("value", Text.color(line)));
        }
        gui.setList(player, "krebbs_comment", howTo);

        List<Map<String, String>> rewards = new ArrayList<Map<String, String>>();
        for (int level = 1; level <= max; level++) {
            String state = level <= data.level ? "obtenu" : level == data.level + 1 ? "prochain" : "verrouille";
            List<String> lines = plugin.toolManager().describe(type.reward(level));
            String status = plugin.msg().text("values.status-" + state);
            Map<String, String> item = entry("niveau", String.valueOf(level));
            item.put("statut", state);
            item.put("couleur", Text.color(plugin.msg().text("values.color-" + state)));
            item.put("etat", status);
            item.put("resume", lines.isEmpty() ? "" : lines.get(0));
            StringBuilder tooltip = new StringBuilder(plugin.msg().text("values.reward-title", "niveau", level));
            for (String line : lines) {
                tooltip.append('\n').append(plugin.msg().text("values.reward-line", "recompense", line));
            }
            tooltip.append("\n\n").append(status);
            item.put("infobulle", tooltip.toString());
            rewards.add(item);
        }
        gui.setList(player, "krebbs_recompenses", rewards);
    }

    /** Joueur sans le client KoriaMC : résumé dans le chat. */
    private void chat(Player player, ToolType type, ToolData data, int page) {
        Object[] kv = plugin.toolManager().vars(type, data);
        plugin.msg().send(player, data.level >= type.getMaxLevel() ? "menu.chat-max" : "menu.chat", kv);
        if (page == PAGE_REWARDS) {
            for (int level = 1; level <= type.getMaxLevel(); level++) {
                String state = level <= data.level ? "obtenu" : level == data.level + 1 ? "prochain" : "verrouille";
                plugin.msg().send(player, "menu.chat-reward", "niveau", level,
                        "couleur", Text.color(plugin.msg().text("values.color-" + state)),
                        "recompense", join(plugin.toolManager().describe(type.reward(level)), ", "));
            }
            return;
        }
        for (String line : type.getHowTo()) {
            plugin.msg().send(player, "menu.chat-howto", "ligne", Text.color(line));
        }
    }

    private static Map<String, String> entry(String key, String value) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(key, value);
        return map;
    }

    private static String join(List<String> lines, String separator) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(line);
        }
        return sb.toString();
    }
}
