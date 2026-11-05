package fr.koriamc.krebbs.tool;

import fr.koriamc.krebbs.KoriaKrebbs;
import fr.koriamc.krebbs.api.event.KrebbsLevelUpEvent;
import fr.koriamc.krebbs.api.event.KrebbsXpGainEvent;
import fr.koriamc.krebbs.config.Settings;
import fr.koriamc.krebbs.hook.VaultHook;
import fr.koriamc.krebbs.model.LevelObjective;
import fr.koriamc.krebbs.model.LevelReward;
import fr.koriamc.krebbs.model.ToolData;
import fr.koriamc.krebbs.model.ToolType;
import fr.koriamc.krebbs.util.Sounds;
import fr.koriamc.krebbs.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * XP, niveaux et récompenses des outils. Les gains d'un tick sont regroupés et appliqués
 * au tick suivant sur l'emplacement de l'outil : Minecraft finit d'abord d'utiliser l'item
 * (usure, lancer de ligne), puis le plugin réécrit sa progression.
 */
public final class ToolManager {

    /** Gains en attente d'un joueur pour un emplacement de son inventaire. */
    private static final class Pending {
        final int slot;
        final ToolType type;
        int amount;

        Pending(int slot, ToolType type) {
            this.slot = slot;
            this.type = type;
        }
    }

    private final KoriaKrebbs plugin;
    private final Map<UUID, List<Pending>> pending = new HashMap<UUID, List<Pending>>();
    private boolean flushScheduled;

    public ToolManager(KoriaKrebbs plugin) {
        this.plugin = plugin;
    }

    // ================================================================ Gains

    /** XP gagnée avec l'outil tenu en main (appliquée au tick suivant). */
    public void gain(Player player, ToolType type, int amount) {
        gain(player, player.getInventory().getHeldItemSlot(), type, amount);
    }

    /**
     * Action signalée pour les outils à objectifs (Toucan) : fait progresser les outils dont
     * l'objectif du prochain niveau correspond.
     *
     * @param heldOnly seulement l'outil tenu en main (tuer, miner, pêcher), sinon tous les
     *                 outils de l'inventaire (vendre, fondre)
     */
    public void progress(Player player, String action, Collection<String> keys, int amount, boolean heldOnly) {
        if (amount <= 0) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int held = inventory.getHeldItemSlot();
        for (int slot = 0; slot < 36; slot++) {
            if (heldOnly && slot != held) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            ToolType type = plugin.tools().of(item);
            if (type == null || !type.hasObjectives()) {
                continue;
            }
            ToolData data = ItemData.read(item);
            LevelObjective objective = type.objective(data.level + 1);
            if (objective != null && objective.matches(action, keys)) {
                gain(player, slot, type, amount);
            }
        }
    }

    /** Pouvoirs actifs de cet objet (vide si ce n'est pas un outil). */
    public Map<String, Double> powers(ItemStack item) {
        ToolType type = plugin.tools().of(item);
        return type == null ? new HashMap<String, Double>() : Powers.of(type, ItemData.read(item));
    }

    /** XP gagnée par l'outil d'un emplacement (appliquée au tick suivant). */
    public void gain(Player player, int slot, ToolType type, int amount) {
        if (amount <= 0 || type == null) {
            return;
        }
        List<Pending> list = pending.get(player.getUniqueId());
        if (list == null) {
            list = new ArrayList<Pending>();
            pending.put(player.getUniqueId(), list);
        }
        Pending target = null;
        for (Pending p : list) {
            if (p.slot == slot && p.type == type) {
                target = p;
            }
        }
        if (target == null) {
            target = new Pending(slot, type);
            list.add(target);
        }
        target.amount += amount;
        if (!flushScheduled) {
            flushScheduled = true;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    flush();
                }
            });
        }
    }

    private void flush() {
        flushScheduled = false;
        Map<UUID, List<Pending>> batch = new HashMap<UUID, List<Pending>>(pending);
        pending.clear();
        for (Map.Entry<UUID, List<Pending>> entry : batch.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            for (Pending p : entry.getValue()) {
                addXp(player, p.slot, p.amount, true);
            }
        }
    }

    public void forget(Player player) {
        pending.remove(player.getUniqueId());
    }

    /**
     * Ajoute de l'XP à l'outil d'un emplacement et donne les récompenses des niveaux atteints.
     *
     * @param event déclencher KrebbsXpGainEvent (les gains donnés par un admin ne passent pas par là)
     * @return le nombre de niveaux gagnés, ou -1 si l'emplacement ne contient pas d'outil
     */
    public int addXp(Player player, int slot, int amount, boolean event) {
        PlayerInventory inventory = player.getInventory();
        ItemStack item = inventory.getItem(slot);
        ToolType type = plugin.tools().of(item);
        if (type == null) {
            return -1;
        }
        ToolData data = ItemData.read(item);
        int max = type.getMaxLevel();
        if (data.level >= max || amount <= 0) {
            return 0;
        }
        if (event) {
            KrebbsXpGainEvent gain = new KrebbsXpGainEvent(player, type.getId(), item.clone(), amount);
            Bukkit.getPluginManager().callEvent(gain);
            if (gain.isCancelled() || gain.getAmount() <= 0) {
                return 0;
            }
            amount = gain.getAmount();
        }
        data.xp += amount;
        List<Integer> reached = new ArrayList<Integer>();
        while (data.level < max && data.xp >= type.required(data.level + 1)) {
            data.xp -= type.required(data.level + 1);
            data.level++;
            reached.add(data.level);
            if (type.hasObjectives()) {
                // chaque niveau a son propre objectif : le surplus ne compte pas pour le suivant
                data.xp = 0;
                break;
            }
        }
        if (data.level >= max) {
            data.xp = 0;
        }
        ItemStack updated = item.clone();
        for (int level : reached) {
            applyEnchants(updated, type.reward(level));
        }
        inventory.setItem(slot, render(updated, type, data));

        if (reached.isEmpty()) {
            if (plugin.settings().actionBar) {
                plugin.msg().send(player, "krebbs.xp-gain", vars(type, data, "gain", amount));
            }
            Sounds.play(player, plugin.settings().sound("gain-xp"));
            return 0;
        }
        for (int level : reached) {
            levelUp(player, type, data, level);
        }
        return reached.size();
    }

    private void levelUp(Player player, ToolType type, ToolData data, int level) {
        LevelReward reward = type.reward(level);
        Object[] kv = vars(type, data, "niveau", level);
        plugin.msg().send(player, "krebbs.level-up", kv);
        for (String line : describe(reward)) {
            plugin.msg().send(player, "krebbs.level-up-reward", "recompense", line);
        }
        if (reward != null) {
            giveRewards(player, type, reward);
        }
        boolean max = level >= type.getMaxLevel();
        Sounds.play(player, plugin.settings().sound(max ? "niveau-max" : "niveau"));
        if (max && plugin.settings().announceMax) {
            plugin.msg().broadcast("krebbs.max-announce", "joueur", player.getName(), "outil", Text.color(type.getName()),
                    "max", type.getMaxLevel());
        }
        Bukkit.getPluginManager().callEvent(new KrebbsLevelUpEvent(player, type.getId(), level));
    }

    /** Argent, commandes, messages et annonce d'un niveau (les enchantements sont déjà sur l'outil). */
    private void giveRewards(Player player, ToolType type, LevelReward reward) {
        Object[] kv = {"joueur", player.getName(), "player", player.getName(), "niveau", reward.getLevel(),
                "outil", Text.color(type.getName()), "id", type.getId()};
        if (reward.getMoney() > 0) {
            VaultHook economy = plugin.economy();
            if (economy == null) {
                plugin.getLogger().warning("Récompense de " + reward.getMoney() + " pour " + player.getName()
                        + " non donnée : Vault ou une économie est absent.");
            } else {
                economy.deposit(player, reward.getMoney());
            }
        }
        for (String command : reward.getCommands()) {
            String line = Text.replace(command, kv);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.startsWith("/") ? line.substring(1) : line);
        }
        for (String message : reward.getMessages()) {
            plugin.msg().sendLine(player, plugin.msg().format(message, kv));
        }
        if (reward.isAnnounce()) {
            plugin.msg().broadcast("krebbs.reward-announce", kv);
        }
    }

    /** Pose les enchantements d'une récompense (sans jamais baisser un niveau existant). */
    private static void applyEnchants(ItemStack item, LevelReward reward) {
        if (reward == null) {
            return;
        }
        for (Map.Entry<Enchantment, Integer> e : reward.getEnchants().entrySet()) {
            if (item.getEnchantmentLevel(e.getKey()) < e.getValue()) {
                item.addUnsafeEnchantment(e.getKey(), e.getValue());
            }
        }
    }

    // ================================================================ Admin

    /** Nouvel outil au niveau donné, avec les enchantements des niveaux déjà atteints. */
    @SuppressWarnings("deprecation")
    public ItemStack create(ToolType type, int level) {
        ItemStack item = new ItemStack(type.getMaterialId(), 1);
        if (item.getType() == Material.AIR) {
            return item;
        }
        return withLevel(item, type, level);
    }

    /** Fixe le niveau (XP remise à 0) et pose les enchantements des niveaux jusqu'à celui-ci. */
    public ItemStack withLevel(ItemStack item, ToolType type, int level) {
        ToolData data = ItemData.read(item);
        data.level = Math.max(0, Math.min(type.getMaxLevel(), level));
        data.xp = 0;
        ItemStack updated = item.clone();
        for (int l = 1; l <= data.level; l++) {
            applyEnchants(updated, type.reward(l));
        }
        return render(updated, type, data);
    }

    // ============================================================ Affichage

    /** Écrit la progression dans l'item et met à jour les lignes du plugin à la fin du lore. */
    public ItemStack render(ItemStack item, ToolType type, ToolData data) {
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
            int remove = Math.min(data.loreLines, lore.size());
            for (int i = 0; i < remove; i++) {
                lore.remove(lore.size() - 1);
            }
            List<String> added = new ArrayList<String>();
            if (plugin.settings().lore) {
                String key = data.level >= type.getMaxLevel() ? "lore-max" : "lore";
                added.addAll(plugin.msg().lines(key, vars(type, data)));
            }
            lore.addAll(added);
            data.loreLines = added.size();
            meta.setLore(lore.isEmpty() ? null : lore);
            result.setItemMeta(meta);
        }
        return ItemData.write(result, data);
    }

    /** Variables d'affichage d'un outil (lore, messages, menu). */
    public Object[] vars(ToolType type, ToolData data, Object... extra) {
        int max = type.getMaxLevel();
        boolean maxed = data.level >= max;
        int required = maxed ? 0 : type.required(data.level + 1);
        int percent = maxed ? 100 : (int) Math.floor(data.xp * 100.0 / Math.max(1, required));
        Object[] base = {
                "outil", Text.color(type.getName()),
                "id", type.getId(),
                "niveau", data.level,
                "max", max,
                "xp", data.xp,
                "requis", required,
                "restant", Math.max(0, required - data.xp),
                "pourcentage", percent,
                "barre", bar(percent),
                "prochain", Math.min(max, data.level + 1),
                "objectif", objectiveText(type, data)
        };
        Object[] all = new Object[base.length + extra.length];
        System.arraycopy(extra, 0, all, 0, extra.length);
        System.arraycopy(base, 0, all, extra.length, base.length);
        return all;
    }

    /** Texte de l'objectif du prochain niveau (vide en mode XP ou au niveau maximum). */
    public String objectiveText(ToolType type, ToolData data) {
        LevelObjective objective = type.objective(data.level + 1);
        return objective == null ? "" : Text.color(objective.getText());
    }

    /** Barre de progression en texte (lore, barre d'action). */
    public String bar(int percent) {
        Settings s = plugin.settings();
        int filled = (int) Math.round(s.barLength * Math.max(0, Math.min(100, percent)) / 100.0);
        StringBuilder sb = new StringBuilder(Text.color(s.barFull));
        for (int i = 0; i < filled; i++) {
            sb.append(s.barChar);
        }
        sb.append(Text.color(s.barEmpty));
        for (int i = filled; i < s.barLength; i++) {
            sb.append(s.barChar);
        }
        return sb.toString();
    }

    /**
     * Description d'une récompense : enchantements et argent décrits automatiquement,
     * puis les lignes "description" d'outils.json.
     */
    public List<String> describe(LevelReward reward) {
        List<String> lines = new ArrayList<String>();
        if (reward == null) {
            lines.add(plugin.msg().text("values.no-reward"));
            return lines;
        }
        for (Map.Entry<Enchantment, Integer> e : reward.getEnchants().entrySet()) {
            String key = "enchantements." + e.getKey().getName();
            String name = plugin.msg().has(key) ? plugin.msg().text(key) : e.getKey().getName();
            lines.add(plugin.msg().text("values.reward-enchant", "enchantement", name, "niveau", roman(e.getValue())));
        }
        if (reward.getMoney() > 0) {
            lines.add(plugin.msg().text("values.reward-money", "montant", Text.number(reward.getMoney(), 0)));
        }
        for (String line : reward.getDescription()) {
            lines.add(Text.color(line));
        }
        if (lines.isEmpty()) {
            lines.add(plugin.msg().text("values.no-reward"));
        }
        return lines;
    }

    /** 1 -> I ... 10 -> X (au-delà : chiffres). */
    public static String roman(int value) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return value >= 1 && value <= 10 ? numerals[value - 1] : String.valueOf(value);
    }
}
