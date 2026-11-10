package fr.koriamc.krebbs.listener;

import fr.koriamc.krebbs.KoriaKrebbs;
import fr.koriamc.krebbs.model.LevelObjective;
import fr.koriamc.krebbs.model.ToolType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gains d'XP des outils (minage, combat, récolte, pêche), anti-abus et ouverture du menu
 * par shift + clic droit.
 */
@SuppressWarnings("deprecation")
public final class ToolListener implements Listener {

    /** Bloc posé par un joueur (pas d'XP en le cassant). */
    private static final String PLACED = "koriakrebbs-pose";
    /** Créature sortie d'un spawner. */
    private static final String SPAWNER = "koriakrebbs-spawner";
    /** Délai entre deux ouvertures du menu : un clic droit sur un bloc arrive parfois deux fois. */
    private static final long MENU_DEBOUNCE_MS = 300;

    private final KoriaKrebbs plugin;
    private final Map<UUID, Long> lastMenu = new HashMap<UUID, Long>();
    /** "tueur:victime" -> date du dernier kill qui a rapporté de l'XP. */
    private final Map<String, Long> lastKill = new HashMap<String, Long>();

    public ToolListener(KoriaKrebbs plugin) {
        this.plugin = plugin;
    }

    // ================================================================ Menu

    /** Shift + clic droit avec un outil : menu (et pas de labour, de lancer de ligne ni de blocage). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.settings().shiftRightClick || !player.isSneaking() || plugin.tools().of(event.getItem()) == null) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        long now = System.currentTimeMillis();
        Long last = lastMenu.get(player.getUniqueId());
        if (last != null && now - last < MENU_DEBOUNCE_MS) {
            return;
        }
        lastMenu.put(player.getUniqueId(), now);
        plugin.menus().open(player, "principal");
    }

    // ============================================================= Minage

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.settings().ignorePlaced) {
            event.getBlock().setMetadata(PLACED, new FixedMetadataValue(plugin, Boolean.TRUE));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean placed = block.hasMetadata(PLACED);
        if (placed) {
            block.removeMetadata(PLACED, plugin);
        }
        Player player = event.getPlayer();
        ToolType type = plugin.tools().of(player.getItemInHand());
        if (type == null || !counts(player)) {
            return;
        }
        int id = block.getTypeId();
        int data = block.getData();
        if (type.hasObjectives()) {
            List<String> keys = keys(block.getType().name(), data);
            Boolean mature = cropState(block.getType(), data);
            if (!placed || !plugin.settings().ignorePlaced) {
                plugin.toolManager().progress(player, LevelObjective.MINE, keys, 1, true);
            }
            if (mature == null ? !placed || !plugin.settings().ignorePlaced : mature) {
                plugin.toolManager().progress(player, LevelObjective.HARVEST, keys, 1, true);
            }
            return;
        }
        if (type.getSource() == ToolType.Source.BLOCS) {
            if (!placed || !plugin.settings().ignorePlaced) {
                plugin.toolManager().gain(player, type, type.xpForBlock(id, data));
            }
        } else if (type.getSource() == ToolType.Source.CULTURES && type.listsBlock(id, data)) {
            Boolean mature = mature(block.getType(), data);
            // cultures qui poussent : mûres seulement ; melon, citrouille... : pas posés par un joueur
            boolean ok = mature != null ? mature : !placed || !plugin.settings().ignorePlaced;
            if (ok) {
                plugin.toolManager().gain(player, type, type.xpForBlock(id, data));
            }
        }
    }

    /** Comme {@link #mature}, en tenant compte des cultures modées de config.json. */
    private Boolean cropState(Material material, int data) {
        Integer max = plugin.settings().moddedCropMax(material);
        return max != null ? data >= max : mature(material, data);
    }

    /** Culture mûre ? null si le bloc ne pousse pas par stades (melon, citrouille, canne...). */
    public static Boolean mature(Material material, int data) {
        switch (material) {
            case CROPS:
            case CARROT:
            case POTATO:
                return data >= 7;
            case NETHER_WARTS:
                return data >= 3;
            case COCOA:
                return (data >> 2) >= 2;
            default:
                return null;
        }
    }

    // ============================================================= Combat

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.getEntity().setMetadata(SPAWNER, new FixedMetadataValue(plugin, Boolean.TRUE));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer == victim || !counts(killer)) {
            return;
        }
        ToolType type = plugin.tools().of(killer.getItemInHand());
        if (type != null && type.hasObjectives()) {
            if (!(victim instanceof Player)) {
                List<String> keys = new ArrayList<String>();
                keys.add(victim.getType().name());
                String custom = mobKey(victim);
                if (custom != null) {
                    keys.add(custom);
                }
                // les mobs de spawner comptent entièrement : les objectifs du Toucan sont prévus pour
                plugin.toolManager().progress(killer, LevelObjective.KILL, keys, 1, true);
            }
            return;
        }
        if (type == null || type.getSource() != ToolType.Source.MOBS) {
            return;
        }
        int xp = type.xpFor(victim.getType().name());
        if (victim instanceof Player) {
            if (!cooldownPassed(killer, victim)) {
                return;
            }
        } else if (victim.hasMetadata(SPAWNER)) {
            xp = (int) Math.floor(xp * plugin.settings().spawnerMultiplier);
        }
        plugin.toolManager().gain(killer, type, xp);
    }

    /** Anti-farm : un même joueur ne rapporte de l'XP qu'une fois par délai. */
    private boolean cooldownPassed(Player killer, Entity victim) {
        int seconds = plugin.settings().sameVictimSeconds;
        if (seconds <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        Long last = lastKill.get(key);
        if (last != null && now - last < seconds * 1000L) {
            return false;
        }
        lastKill.put(key, now);
        if (lastKill.size() > 2048) {
            Iterator<Long> it = lastKill.values().iterator();
            while (it.hasNext()) {
                if (now - it.next() >= seconds * 1000L) {
                    it.remove();
                }
            }
        }
        return true;
    }

    // ============================================================== Pêche

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item)) {
            return;
        }
        Player player = event.getPlayer();
        ToolType type = plugin.tools().of(player.getItemInHand());
        if (type != null && type.hasObjectives()) {
            if (counts(player)) {
                ItemStack stack = ((Item) event.getCaught()).getItemStack();
                List<String> keys = keys(stack.getType().name(), stack.getDurability());
                String tier = plugin.fish().tierOf(stack);
                if (tier != null) {
                    keys.add(tier);
                }
                plugin.toolManager().progress(player, LevelObjective.FISH, keys, stack.getAmount(), true);
            }
            return;
        }
        if (type == null || type.getSource() != ToolType.Source.PECHE || !counts(player)) {
            return;
        }
        Material caught = ((Item) event.getCaught()).getItemStack().getType();
        plugin.toolManager().gain(player, type, type.xpFor(caught.name()));
    }

    // ===================================================== Four (fondre, cuire)

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (event.getItemAmount() <= 0 || !counts(player)) {
            return;
        }
        List<String> keys = keys(event.getItemType().name(), -1);
        plugin.toolManager().progress(player, LevelObjective.SMELT, keys, event.getItemAmount(), false);
    }

    /** Clés d'un matériau : NOM et NOM:data (data < 0 : nom seul). */
    private static List<String> keys(String material, int data) {
        List<String> keys = new ArrayList<String>();
        keys.add(material);
        if (data >= 0) {
            keys.add(material + ":" + data);
        }
        return keys;
    }

    /**
     * Nom d'un mob custom (KOALA...) : type d'entité custom du serveur, sinon nom affiché
     * sans couleurs, en majuscules et sans espaces.
     */
    public static String mobKey(Entity entity) {
        String custom = entity.getCustomName();
        if (custom == null || custom.isEmpty()) {
            return null;
        }
        return org.bukkit.ChatColor.stripColor(custom).trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    // ============================================================== Divers

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastMenu.remove(event.getPlayer().getUniqueId());
        plugin.toolManager().forget(event.getPlayer());
    }

    /** Pas d'XP en créatif ou spectateur (sauf réglage contraire). */
    private boolean counts(Player player) {
        GameMode mode = player.getGameMode();
        return plugin.settings().creative || (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR);
    }
}
