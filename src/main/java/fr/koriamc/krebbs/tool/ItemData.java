package fr.koriamc.krebbs.tool;

import fr.koriamc.krebbs.model.ToolData;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Lecture / écriture de la progression dans le NBT de l'item (tag "KoriaKrebbs"), par
 * réflexion sur CraftItemStack pour rester compatible avec les forks. La progression
 * suit donc l'outil : échange, coffre, mort, hôtel des ventes...
 */
public final class ItemData {

    public static final String TAG = "KoriaKrebbs";

    private static boolean initialized;
    private static boolean available;

    private static Method asNMSCopy;
    private static Method asBukkitCopy;
    private static Method hasTag;
    private static Method getTag;
    private static Method setTag;
    private static Constructor<?> newCompound;
    private static Method hasKey;
    private static Method getCompound;
    private static Method set;
    private static Method setInt;
    private static Method getInt;

    private ItemData() {
    }

    private static void init() {
        initialized = true;
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.substring(pkg.lastIndexOf('.') + 1);
            String nms = "net.minecraft.server." + version + ".";
            Class<?> craftItem = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Class<?> nmsItem = Class.forName(nms + "ItemStack");
            Class<?> compound = Class.forName(nms + "NBTTagCompound");
            Class<?> base = Class.forName(nms + "NBTBase");
            asNMSCopy = craftItem.getMethod("asNMSCopy", ItemStack.class);
            asBukkitCopy = craftItem.getMethod("asBukkitCopy", nmsItem);
            hasTag = nmsItem.getMethod("hasTag");
            getTag = nmsItem.getMethod("getTag");
            setTag = nmsItem.getMethod("setTag", compound);
            newCompound = compound.getConstructor();
            hasKey = compound.getMethod("hasKey", String.class);
            getCompound = compound.getMethod("getCompound", String.class);
            set = compound.getMethod("set", String.class, base);
            setInt = compound.getMethod("setInt", String.class, int.class);
            getInt = compound.getMethod("getInt", String.class);
            available = true;
        } catch (Throwable t) {
            available = false;
            Bukkit.getLogger().log(Level.SEVERE, "[KoriaKrebbs] Accès NBT impossible : la progression des outils ne peut pas être enregistrée.", t);
        }
    }

    public static boolean isAvailable() {
        if (!initialized) {
            init();
        }
        return available;
    }

    /** Progression de l'item (niveau 0 si l'outil n'a encore rien gagné). */
    public static ToolData read(ItemStack item) {
        if (item == null || !isAvailable()) {
            return ToolData.fresh();
        }
        try {
            Object nms = asNMSCopy.invoke(null, item);
            if (nms == null || !(Boolean) hasTag.invoke(nms)) {
                return ToolData.fresh();
            }
            Object tag = getTag.invoke(nms);
            if (!(Boolean) hasKey.invoke(tag, TAG)) {
                return ToolData.fresh();
            }
            Object data = getCompound.invoke(tag, TAG);
            return new ToolData(Math.max(0, (Integer) getInt.invoke(data, "niveau")),
                    Math.max(0, (Integer) getInt.invoke(data, "xp")),
                    Math.max(0, (Integer) getInt.invoke(data, "lignes")));
        } catch (Throwable t) {
            return ToolData.fresh();
        }
    }

    /** @return une copie de l'item portant cette progression (l'original n'est pas modifié) */
    public static ItemStack write(ItemStack item, ToolData data) {
        if (item == null || !isAvailable()) {
            return item;
        }
        try {
            Object nms = asNMSCopy.invoke(null, item);
            if (nms == null) {
                return item;
            }
            Object tag = (Boolean) hasTag.invoke(nms) ? getTag.invoke(nms) : newCompound.newInstance();
            Object compound = newCompound.newInstance();
            setInt.invoke(compound, "niveau", data.level);
            setInt.invoke(compound, "xp", data.xp);
            setInt.invoke(compound, "lignes", data.loreLines);
            set.invoke(tag, TAG, compound);
            setTag.invoke(nms, tag);
            return (ItemStack) asBukkitCopy.invoke(null, nms);
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[KoriaKrebbs] Écriture NBT impossible", t);
            return item;
        }
    }
}
