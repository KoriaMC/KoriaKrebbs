package fr.koriamc.krebbs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Un outil Krebbs va gagner de l'XP. Le montant peut être modifié (bonus, grade...)
 * ou l'évènement annulé.
 */
public final class KrebbsXpGainEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String toolId;
    private final ItemStack item;
    private int amount;
    private boolean cancelled;

    public KrebbsXpGainEvent(Player player, String toolId, ItemStack item, int amount) {
        super(player);
        this.toolId = toolId;
        this.item = item;
        this.amount = amount;
    }

    /** Identifiant de l'outil dans outils.json (pickaxe, sword...). */
    public String getToolId() {
        return toolId;
    }

    /** Copie de l'outil avant le gain. */
    public ItemStack getItem() {
        return item;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
