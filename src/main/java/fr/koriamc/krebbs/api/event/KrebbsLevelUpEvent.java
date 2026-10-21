package fr.koriamc.krebbs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Un outil Krebbs vient de passer un niveau (récompenses déjà données).
 */
public final class KrebbsLevelUpEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String toolId;
    private final int level;

    public KrebbsLevelUpEvent(Player player, String toolId, int level) {
        super(player);
        this.toolId = toolId;
        this.level = level;
    }

    /** Identifiant de l'outil dans outils.json (pickaxe, sword...). */
    public String getToolId() {
        return toolId;
    }

    /** Niveau atteint. */
    public int getLevel() {
        return level;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
