package fr.koriamc.krebbs.command.impl;

import fr.koriamc.krebbs.KoriaKrebbs;
import fr.koriamc.krebbs.command.CommandContext;
import fr.koriamc.krebbs.command.SubCommand;

/**
 * Commandes des joueurs : aide, menu de l'outil, récompenses.
 */
public final class PlayerCommands {

    private PlayerCommands() {
    }

    /** /krebbs help */
    public static final class Help extends SubCommand {
        public Help(KoriaKrebbs plugin) {
            super(plugin, "help", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            ctx.msg("help.header");
            for (SubCommand sub : plugin.commands().visible(ctx.sender)) {
                ctx.msg("help.line", "command", sub.getId(), "usage", sub.getUsage(), "description", sub.getDescription());
            }
            ctx.msg("help.footer");
        }
    }

    /** /krebbs menu : menu de l'outil tenu en main */
    public static final class OpenMenu extends SubCommand {
        public OpenMenu(KoriaKrebbs plugin) {
            super(plugin, "menu", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.menus().open(ctx.player, "principal");
        }
    }

    /** /krebbs rewards : récompenses des niveaux de l'outil tenu en main */
    public static final class Rewards extends SubCommand {
        public Rewards(KoriaKrebbs plugin) {
            super(plugin, "rewards", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.menus().open(ctx.player, "recompenses");
        }
    }
}
