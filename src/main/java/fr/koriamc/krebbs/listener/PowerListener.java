package fr.koriamc.krebbs.listener;

import fr.koriamc.krebbs.KoriaKrebbs;
import fr.koriamc.krebbs.hook.VaultHook;
import fr.koriamc.krebbs.tool.Powers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Effets des pouvoirs débloqués par les niveaux des outils du Toucan (voir {@link Powers}).
 */
@SuppressWarnings("deprecation")
public final class PowerListener implements Listener {

    private final KoriaKrebbs plugin;
    private final Random random = new Random();
    /** Joueurs dont on casse/laboure des blocs en zone : évite les effets en cascade. */
    private final Set<UUID> busy = new HashSet<UUID>();
    /** Mobs tués par un multi-kill (pas de nouveau multi-kill sur eux). */
    private final Set<UUID> chained = new HashSet<UUID>();
    /** Blocs cassés ce tick par un outil avec télékinésie : "monde:x:y:z" -> joueur. */
    private final Map<String, UUID> telekinesis = new HashMap<String, UUID>();
    private boolean clearScheduled;

    public PowerListener(KoriaKrebbs plugin) {
        this.plugin = plugin;
    }

    private Map<String, Double> powers(Player player) {
        return plugin.toolManager().powers(player.getItemInHand());
    }

    // =================================================================== Épée

    /** COUPS_MOBS, ONESHOT_KOALA, ONESHOT_ENDERMAN. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)
                || event.getEntity() instanceof Player) {
            return;
        }
        Player player = (Player) event.getDamager();
        Map<String, Double> powers = powers(player);
        if (powers.isEmpty()) {
            return;
        }
        LivingEntity victim = (LivingEntity) event.getEntity();
        boolean enderman = victim.getType() == EntityType.ENDERMAN;
        boolean koala = isKoala(victim);
        if ((enderman && Powers.value(powers, Powers.ONESHOT_ENDERMAN) > 0)
                || (koala && Powers.value(powers, Powers.ONESHOT_KOALA) > 0)) {
            event.setDamage(victim.getHealth() * 2 + 10);
            return;
        }
        double hits = Powers.value(powers, Powers.MOB_HITS);
        if (hits <= 0 || enderman || koala) {
            return;
        }
        // marge pour l'armure naturelle de certains mobs
        double needed = victim.getMaxHealth() / hits * 1.25 + 1;
        if (event.getDamage() < needed) {
            event.setDamage(needed);
        }
    }

    /** ARGENT_MOB, TELEKINESIE, MULTI_KILL. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || victim instanceof Player) {
            return;
        }
        Map<String, Double> powers = powers(killer);
        if (powers.isEmpty()) {
            return;
        }
        deposit(killer, Powers.value(powers, Powers.MOB_MONEY));
        if (Powers.value(powers, Powers.TELEKINESIS) > 0) {
            giveAll(killer, event.getDrops());
            event.getDrops().clear();
        }
        int multi = (int) Powers.value(powers, Powers.MULTI_KILL);
        if (multi > 1 && !chained.remove(victim.getUniqueId())) {
            int extra = multi - 1;
            for (Entity near : victim.getNearbyEntities(4, 3, 4)) {
                if (extra <= 0) {
                    break;
                }
                if (near.getType() != victim.getType() || near.isDead() || !(near instanceof LivingEntity)) {
                    continue;
                }
                LivingEntity other = (LivingEntity) near;
                chained.add(other.getUniqueId());
                other.damage(other.getHealth() * 2 + 10, killer);
                if (!other.isDead()) {
                    chained.remove(other.getUniqueId());
                }
                extra--;
            }
        }
    }

    private static boolean isKoala(Entity entity) {
        if (entity.getType().name().equals("KOALA")) {
            return true;
        }
        String key = ToolListener.mobKey(entity);
        return key != null && key.contains("KOALA");
    }

    // ======================================================= Pioche et houe

    /** XP_MINAGE : avant la casse, pour que l'XP tombe avec le bloc. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakXp(BlockBreakEvent event) {
        double xp = Powers.value(powers(event.getPlayer()), Powers.MINE_XP);
        if (xp > 0 && event.getBlock().getType().isSolid()) {
            event.setExpToDrop(event.getExpToDrop() + (int) Math.round(xp));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getItemInHand();
        Map<String, Double> powers = plugin.toolManager().powers(tool);
        if (powers.isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        String type = block.getType().name();
        if (Powers.value(powers, Powers.TELEKINESIS) > 0) {
            catchDrops(player, block);
        }
        deposit(player, Powers.value(powers, Powers.BLOCK_MONEY));

        double combo = Powers.value(powers, Powers.COMBO);
        if (combo > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, plugin.settings().comboTicks,
                    (int) combo - 1, true, false), true);
        }
        double duplicate = Powers.value(powers, Powers.DUPLICATE_ORES);
        if (duplicate > 0 && plugin.settings().moddedOres.contains(type) && roll(duplicate)) {
            dropAll(block, block.getDrops(tool));
        }

        Boolean mature = cropState(block);
        boolean modded = plugin.settings().moddedCrops.containsKey(type);
        boolean cropsAllowed = !modded || Powers.value(powers, Powers.MODDED_CROPS) > 0;
        if (Boolean.TRUE.equals(mature) && cropsAllowed) {
            int fortune = (int) Powers.value(powers, modded ? Powers.MODDED_CROP_FORTUNE : Powers.CROP_FORTUNE);
            if (fortune > 0) {
                ItemStack product = product(block, tool);
                if (product != null) {
                    int bonus = random.nextInt(fortune + 1);
                    if (bonus > 0) {
                        product.setAmount(bonus);
                        block.getWorld().dropItemNaturally(center(block), product);
                    }
                }
            }
            double petals = Powers.value(powers, Powers.PETALS);
            if (petals > 0 && roll(petals)) {
                Material petal = Material.matchMaterial(plugin.settings().petalItem.toUpperCase(Locale.ROOT));
                if (petal != null) {
                    block.getWorld().dropItemNaturally(center(block), new ItemStack(petal));
                }
            }
            if (Powers.value(powers, Powers.REPLANT) > 0) {
                replantLater(player, block);
            }
        }

        if (busy.contains(player.getUniqueId())) {
            return;
        }
        List<Block> extra = new ArrayList<Block>();
        int mineArea = (int) Powers.value(powers, Powers.MINE_AREA);
        if (mineArea >= 2 && mature == null && !modded && block.getType().isSolid()) {
            Block below = block.getRelative(BlockFace.DOWN);
            if (minable(below)) {
                extra.add(below);
            }
            if (mineArea >= 3) {
                Block above = block.getRelative(BlockFace.UP);
                if (minable(above)) {
                    extra.add(above);
                }
            }
        }
        int harvestArea = (int) Powers.value(powers, Powers.HARVEST_AREA);
        if (harvestArea >= 2 && Boolean.TRUE.equals(mature) && cropsAllowed) {
            for (Block b : area(block, player, harvestArea)) {
                if (b.getType() == block.getType() && Boolean.TRUE.equals(cropState(b))) {
                    extra.add(b);
                }
            }
        }
        breakExtra(player, tool, extra);
    }

    /** Casse des blocs en plus en passant par BlockBreakEvent (protections, quêtes, objectifs). */
    private void breakExtra(Player player, ItemStack tool, List<Block> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        busy.add(player.getUniqueId());
        try {
            for (Block b : blocks) {
                BlockBreakEvent extra = new BlockBreakEvent(b, player);
                Bukkit.getPluginManager().callEvent(extra);
                if (!extra.isCancelled()) {
                    b.breakNaturally(tool);
                }
            }
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    private static boolean minable(Block block) {
        Material m = block.getType();
        return m.isSolid() && m != Material.BEDROCK && m != Material.OBSIDIAN && m != Material.MOB_SPAWNER
                && m != Material.ENDER_PORTAL_FRAME && !(block.getState() instanceof org.bukkit.inventory.InventoryHolder);
    }

    /** Blocs autour : 2 = celui dans la direction du regard, 3 = carré 3x3 autour. */
    private static List<Block> area(Block center, Player player, int size) {
        List<Block> out = new ArrayList<Block>();
        if (size >= 3) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        out.add(center.getRelative(dx, 0, dz));
                    }
                }
            }
        } else {
            out.add(center.getRelative(facing(player)));
        }
        return out;
    }

    private static BlockFace facing(Player player) {
        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        if (yaw >= 45 && yaw < 135) {
            return BlockFace.WEST;
        }
        if (yaw >= 135 && yaw < 225) {
            return BlockFace.NORTH;
        }
        if (yaw >= 225 && yaw < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    private Boolean cropState(Block block) {
        Integer max = plugin.settings().moddedCrops.get(block.getType().name());
        return max != null ? block.getData() >= max : ToolListener.mature(block.getType(), block.getData());
    }

    /** Produit principal d'une culture (blé, carotte, patate, verrue ; premier drop sinon). */
    private static ItemStack product(Block block, ItemStack tool) {
        switch (block.getType()) {
            case CROPS:
                return new ItemStack(Material.WHEAT);
            case CARROT:
                return new ItemStack(Material.CARROT_ITEM);
            case POTATO:
                return new ItemStack(Material.POTATO_ITEM);
            case NETHER_WARTS:
                return new ItemStack(Material.NETHER_STALK);
            default:
                Collection<ItemStack> drops = block.getDrops(tool);
                return drops.isEmpty() ? null : drops.iterator().next().clone();
        }
    }

    /** Graine qui replante une culture, ou null. */
    private static Material seed(Material crop) {
        switch (crop) {
            case CROPS:
                return Material.SEEDS;
            case CARROT:
                return Material.CARROT_ITEM;
            case POTATO:
                return Material.POTATO_ITEM;
            case NETHER_WARTS:
                return Material.NETHER_STALK;
            default:
                return null;
        }
    }

    /** REPLANTATION : au tick suivant (après les drops et la télékinésie), avec une graine de l'inventaire. */
    private void replantLater(final Player player, final Block block) {
        final Material crop = block.getType();
        final Material seed = seed(crop);
        if (seed == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                Material soil = block.getRelative(BlockFace.DOWN).getType();
                boolean soilOk = crop == Material.NETHER_WARTS ? soil == Material.SOUL_SAND : soil == Material.SOIL;
                if (!player.isOnline() || block.getType() != Material.AIR || !soilOk
                        || !player.getInventory().containsAtLeast(new ItemStack(seed), 1)) {
                    return;
                }
                player.getInventory().removeItem(new ItemStack(seed, 1));
                block.setTypeIdAndData(crop.getId(), (byte) 0, true);
            }
        });
    }

    /** LABOUR : laboure aussi les blocs autour (1x2 ou 3x3). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTill(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.useItemInHand() == Event.Result.DENY
                || event.getClickedBlock() == null || event.getPlayer().isSneaking()) {
            return;
        }
        final Player player = event.getPlayer();
        if (busy.contains(player.getUniqueId()) || !tillable(event.getClickedBlock())) {
            return;
        }
        int size = (int) Powers.value(powers(player), Powers.TILL_AREA);
        if (size < 2) {
            return;
        }
        final List<Block> targets = area(event.getClickedBlock(), player, size);
        busy.add(player.getUniqueId());
        try {
            for (Block b : targets) {
                if (!tillable(b)) {
                    continue;
                }
                // les protections (factions, régions) refusent l'interaction si le joueur n'a pas le droit
                PlayerInteractEvent check = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                        player.getItemInHand(), b, BlockFace.UP);
                Bukkit.getPluginManager().callEvent(check);
                if (!check.isCancelled() && check.useInteractedBlock() != Event.Result.DENY) {
                    b.setType(Material.SOIL);
                }
            }
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    private static boolean tillable(Block block) {
        Material m = block.getType();
        return (m == Material.DIRT || m == Material.GRASS) && block.getRelative(BlockFace.UP).getType() == Material.AIR;
    }

    // ================================================================ Canne

    /** POISSONS, DOUBLE_POISSON, TELEKINESIE (avant le comptage des objectifs, en MONITOR). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item)) {
            return;
        }
        Player player = event.getPlayer();
        Map<String, Double> powers = powers(player);
        if (powers.isEmpty()) {
            return;
        }
        Item caught = (Item) event.getCaught();
        int tier = (int) Powers.value(powers, Powers.FISH_TIER);
        if (tier > 0) {
            ItemStack custom = plugin.fish().roll(tier);
            if (custom != null) {
                caught.setItemStack(custom);
            }
        }
        double doubled = Powers.value(powers, Powers.DOUBLE_FISH);
        if (doubled > 0 && roll(doubled)) {
            ItemStack stack = caught.getItemStack();
            stack.setAmount(stack.getAmount() * 2);
            caught.setItemStack(stack);
        }
        if (Powers.value(powers, Powers.TELEKINESIS) > 0) {
            List<ItemStack> list = new ArrayList<ItemStack>();
            list.add(caught.getItemStack().clone());
            giveAll(player, list);
            // l'entité reste pour les autres plugins de ce tick, puis disparaît
            final Item entity = caught;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    entity.remove();
                }
            });
            caught.setPickupDelay(Integer.MAX_VALUE);
        }
    }

    // =========================================================== Télékinésie

    private void catchDrops(Player player, Block block) {
        telekinesis.put(key(block.getLocation()), player.getUniqueId());
        if (!clearScheduled) {
            clearScheduled = true;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    telekinesis.clear();
                    clearScheduled = false;
                }
            });
        }
    }

    /** Les objets qui apparaissent dans un bloc cassé avec télékinésie vont dans l'inventaire. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (telekinesis.isEmpty()) {
            return;
        }
        UUID owner = telekinesis.get(key(event.getLocation()));
        Player player = owner == null ? null : Bukkit.getPlayer(owner);
        if (player == null) {
            return;
        }
        List<ItemStack> list = new ArrayList<ItemStack>();
        list.add(event.getEntity().getItemStack());
        event.setCancelled(true);
        giveAll(player, list);
    }

    private void giveAll(Player player, Collection<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());
            if (plugin.settings().telekinesisFullDrop) {
                for (ItemStack rest : left.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
        }
    }

    // ================================================================ Outils

    private void dropAll(Block block, Collection<ItemStack> items) {
        for (ItemStack item : items) {
            block.getWorld().dropItemNaturally(center(block), item);
        }
    }

    private static Location center(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    private static String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":"
                + location.getBlockZ();
    }

    private boolean roll(double percent) {
        return random.nextDouble() * 100.0 < percent;
    }

    private void deposit(Player player, double amount) {
        if (amount <= 0) {
            return;
        }
        VaultHook economy = plugin.economy();
        if (economy != null) {
            economy.deposit(player, amount);
        }
    }
}
