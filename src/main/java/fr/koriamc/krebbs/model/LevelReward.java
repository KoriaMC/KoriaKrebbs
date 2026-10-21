package fr.koriamc.krebbs.model;

import org.bukkit.enchantments.Enchantment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Récompense d'un niveau (outils.json : &lt;outil&gt;.recompenses.&lt;niveau&gt;).
 * Les enchantements sont posés sur l'outil, le reste est donné au joueur.
 */
public final class LevelReward {

    private final int level;
    private final Map<Enchantment, Integer> enchants;
    private final double money;
    private final List<String> commands;
    private final List<String> messages;
    private final List<String> description;
    private final boolean announce;

    public LevelReward(int level, Map<Enchantment, Integer> enchants, double money, List<String> commands,
                       List<String> messages, List<String> description, boolean announce) {
        this.level = level;
        this.enchants = Collections.unmodifiableMap(new LinkedHashMap<Enchantment, Integer>(enchants));
        this.money = money;
        this.commands = Collections.unmodifiableList(commands);
        this.messages = Collections.unmodifiableList(messages);
        this.description = Collections.unmodifiableList(description);
        this.announce = announce;
    }

    public int getLevel() {
        return level;
    }

    /** Enchantement -> niveau minimal à atteindre sur l'outil. */
    public Map<Enchantment, Integer> getEnchants() {
        return enchants;
    }

    public double getMoney() {
        return money;
    }

    /** Commandes exécutées par la console ({joueur}, {niveau}, {outil}). */
    public List<String> getCommands() {
        return commands;
    }

    /** Messages envoyés au joueur. */
    public List<String> getMessages() {
        return messages;
    }

    /** Lignes ajoutées à la description automatique (utile pour les commandes). */
    public List<String> getDescription() {
        return description;
    }

    /** Annonce à tout le serveur (messages.json : krebbs.reward-announce). */
    public boolean isAnnounce() {
        return announce;
    }
}
