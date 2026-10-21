package fr.koriamc.krebbs.command.impl;

import fr.koriamc.krebbs.KoriaKrebbs;
import fr.koriamc.krebbs.command.CommandContext;
import fr.koriamc.krebbs.command.SubCommand;
import fr.koriamc.krebbs.config.JsonFile;
import fr.koriamc.krebbs.model.ToolType;
import fr.koriamc.krebbs.tool.ItemData;
import fr.koriamc.krebbs.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Commandes d'administration : donner un outil, ajouter de l'XP, fixer un niveau,
 * éditeur de configuration et rechargement.
 */
public final class AdminCommands {

    private AdminCommands() {
    }

    static List<String> onlineNames(String input) {
        List<String> names = new ArrayList<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return Text.complete(input, names);
    }

    /** Joueur en ligne ou message d'erreur (null). */
    static Player target(CommandContext ctx, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            player = Bukkit.getPlayer(name);
        }
        if (player == null) {
            ctx.msg("general.unknown-player", "player", name);
        }
        return player;
    }

    /** L'outil tenu par le joueur, ou message d'erreur (null). */
    static ToolType held(CommandContext ctx, KoriaKrebbs plugin, Player player) {
        ToolType type = plugin.tools().of(player.getItemInHand());
        if (type == null) {
            ctx.msg("admin.target-not-holding", "player", player.getName());
        }
        return type;
    }

    /** /krebbs give <joueur> <outil> [niveau] */
    public static final class Give extends SubCommand {
        public Give(KoriaKrebbs plugin) {
            super(plugin, "give", false, 2, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            Player player = target(ctx, ctx.arg(0));
            if (player == null) {
                return;
            }
            ToolType type = plugin.tools().get(ctx.arg(1));
            if (type == null) {
                ctx.msg("admin.unknown-tool", "outil", ctx.arg(1), "outils", Text.join(plugin.tools().ids(), ", "));
                return;
            }
            int level = 0;
            if (ctx.size() > 2) {
                Integer parsed = Text.parseInt(ctx.arg(2));
                if (parsed == null || parsed < 0 || parsed > type.getMaxLevel()) {
                    ctx.msg("admin.invalid-level", "input", ctx.arg(2), "max", type.getMaxLevel());
                    return;
                }
                level = parsed;
            }
            ItemStack item = plugin.toolManager().create(type, level);
            Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            for (ItemStack rest : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            ctx.msg("admin.given", "player", player.getName(), "outil", Text.color(type.getName()), "niveau", level);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 1) {
                return onlineNames(ctx.arg(0));
            }
            if (ctx.size() == 2) {
                return Text.complete(ctx.arg(1), plugin.tools().ids());
            }
            return Collections.emptyList();
        }
    }

    /** /krebbs xp <joueur> <montant> : XP ajoutée à l'outil tenu (récompenses comprises) */
    public static final class AddXp extends SubCommand {
        public AddXp(KoriaKrebbs plugin) {
            super(plugin, "xp", false, 2, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            Player player = target(ctx, ctx.arg(0));
            if (player == null) {
                return;
            }
            Integer amount = Text.parseInt(ctx.arg(1));
            if (amount == null || amount <= 0) {
                ctx.msg("general.invalid-number", "input", ctx.arg(1));
                return;
            }
            ToolType type = held(ctx, plugin, player);
            if (type == null) {
                return;
            }
            int levels = plugin.toolManager().addXp(player, player.getInventory().getHeldItemSlot(), amount, false);
            ctx.msg("admin.xp-added", "player", player.getName(), "outil", Text.color(type.getName()), "xp", amount,
                    "niveaux", Math.max(0, levels), "niveau", ItemData.read(player.getItemInHand()).level);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? onlineNames(ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    /** /krebbs level <joueur> <niveau> : fixe le niveau de l'outil tenu (enchantements compris, sans argent ni commandes) */
    public static final class SetLevel extends SubCommand {
        public SetLevel(KoriaKrebbs plugin) {
            super(plugin, "level", false, 2, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            Player player = target(ctx, ctx.arg(0));
            if (player == null) {
                return;
            }
            ToolType type = held(ctx, plugin, player);
            if (type == null) {
                return;
            }
            Integer level = Text.parseInt(ctx.arg(1));
            if (level == null || level < 0 || level > type.getMaxLevel()) {
                ctx.msg("admin.invalid-level", "input", ctx.arg(1), "max", type.getMaxLevel());
                return;
            }
            player.setItemInHand(plugin.toolManager().withLevel(player.getItemInHand(), type, level));
            ctx.msg("admin.level-set", "player", player.getName(), "outil", Text.color(type.getName()), "niveau", level);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? onlineNames(ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    /** /krebbs config [fichier] */
    public static final class Config extends SubCommand {
        public Config(KoriaKrebbs plugin) {
            super(plugin, "config", true, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (!plugin.menus().openConfig(ctx.player, ctx.arg(0))) {
                ctx.msg("config-editor.unknown-file", "file", ctx.arg(0));
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            List<String> names = new ArrayList<String>();
            for (JsonFile file : plugin.configFiles()) {
                names.add(file.getName().replace(".json", ""));
            }
            return ctx.size() == 1 ? Text.complete(ctx.arg(0), names) : Collections.<String>emptyList();
        }
    }

    /** /krebbs reload */
    public static final class Reload extends SubCommand {
        public Reload(KoriaKrebbs plugin) {
            super(plugin, "reload", false, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            List<String> problems = plugin.reload();
            ctx.msg("general.reload-done", "outils", plugin.tools().count());
            if (!problems.isEmpty()) {
                ctx.msg("general.reload-problems", "count", problems.size());
                for (String problem : problems) {
                    ctx.msg("general.reload-problem", "problem", problem);
                }
            }
        }
    }
}
