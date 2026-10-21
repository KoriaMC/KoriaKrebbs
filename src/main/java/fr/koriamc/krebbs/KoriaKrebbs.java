package fr.koriamc.krebbs;

import fr.koriamc.krebbs.command.CommandManager;
import fr.koriamc.krebbs.config.ConfigDocs;
import fr.koriamc.krebbs.config.JsonFile;
import fr.koriamc.krebbs.config.Messages;
import fr.koriamc.krebbs.config.Settings;
import fr.koriamc.krebbs.config.ToolCatalog;
import fr.koriamc.krebbs.gui.Menu;
import fr.koriamc.krebbs.gui.MenuManager;
import fr.koriamc.krebbs.hook.GuiHook;
import fr.koriamc.krebbs.hook.VaultHook;
import fr.koriamc.krebbs.listener.ToolListener;
import fr.koriamc.krebbs.tool.ItemData;
import fr.koriamc.krebbs.tool.ToolManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * KoriaKrebbs : outils Krebbs à niveaux pour KoriaMC (Spigot 1.8.8). Chaque outil gagne de
 * l'XP en servant (minage, combat, récolte, pêche), monte jusqu'au niveau 20 et débloque
 * une récompense par niveau. Shift + clic droit ouvre son menu custom.
 */
public final class KoriaKrebbs extends JavaPlugin {

    private static KoriaKrebbs instance;

    // Fichiers
    private JsonFile configFile;
    private JsonFile toolsFile;
    private JsonFile messagesFile;
    private JsonFile menusFile;
    private Configuration config = new MemoryConfiguration();
    private Configuration messagesConfig = new MemoryConfiguration();
    private Configuration menusConfig = new MemoryConfiguration();

    // Réglages
    private final Settings settings = new Settings();
    private final ToolCatalog tools = new ToolCatalog();
    private final Messages messages = new Messages();
    private final ConfigDocs configDocs = new ConfigDocs();

    // Services
    private ToolManager toolManager;
    private MenuManager menus;
    private CommandManager commands;
    private GuiHook gui;
    private VaultHook vault;
    private boolean vaultChecked;

    public static KoriaKrebbs get() {
        return instance;
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;

        configFile = new JsonFile(this, "config.json");
        toolsFile = new JsonFile(this, "outils.json", "*", "*.xp", "*.recompenses", "*.recompenses.*",
                "*.recompenses.*.enchantements");
        messagesFile = new JsonFile(this, "messages.json");
        menusFile = new JsonFile(this, "menus.json", "editeur.fichiers");

        gui = new GuiHook(getLogger());
        toolManager = new ToolManager(this);
        menus = new MenuManager(this);
        commands = new CommandManager(this);

        for (String problem : loadConfigs()) {
            getLogger().warning(problem);
        }
        configDocs.load(this);
        configDocs.writeReadme(this, configFiles());
        commands.load(config, messagesConfig);

        if (!ItemData.isAvailable()) {
            getLogger().severe("Les niveaux des outils ne peuvent pas être enregistrés sur ce serveur.");
        }
        if (menus.installLayout()) {
            gui.reloadLayouts();
        }
        if (!gui.isAvailable()) {
            getLogger().warning("Serveur sans GUI KoriaMC : le menu des outils est remplacé par un résumé dans le chat.");
        }

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ToolListener(this), this);
        pm.registerEvents(menus, this);
        pm.registerEvents(menus.prompt(), this);
        commands.registerBukkitCommand();

        getLogger().info("KoriaKrebbs activé en " + (System.currentTimeMillis() - start) + " ms ("
                + tools.count() + " outil(s) à niveaux).");
    }

    @Override
    public void onDisable() {
        if (commands != null) {
            commands.unregisterBukkitCommand();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        instance = null;
    }

    /** @return les problèmes à signaler (erreurs JSON, outils invalides) */
    private List<String> loadConfigs() {
        List<String> problems = new ArrayList<String>();
        for (JsonFile file : configFiles()) {
            String error = file.load();
            if (error != null) {
                problems.add(file.getName() + " : " + error);
            }
        }
        config = configFile.toConfiguration();
        messagesConfig = messagesFile.toConfiguration();
        menusConfig = menusFile.toConfiguration();

        settings.load(config, getLogger());
        messages.load(messagesConfig, settings);
        // outils.json sans valeurs du jar : un outil ou une récompense retiré reste retiré
        Configuration toolsConfig = toolsFile.toConfiguration();
        toolsConfig.setDefaults(new MemoryConfiguration());
        tools.load(toolsConfig, settings.required);
        problems.addAll(tools.getProblems());
        return problems;
    }

    /**
     * Recharge toute la configuration (/krebbs reload, éditeur).
     *
     * @return les problèmes détectés, à afficher à l'auteur de la commande
     */
    public List<String> reload() {
        List<String> problems = loadConfigs();
        if (commands != null) {
            commands.load(config, messagesConfig);
        }
        if (menus != null && menus.installLayout()) {
            gui.reloadLayouts();
        }
        return problems;
    }

    /** Économie Vault pour les récompenses en argent, ou null si Vault ou une économie manque. */
    public VaultHook economy() {
        if (vault == null && !vaultChecked && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            vaultChecked = true;
            vault = VaultHook.create();
        }
        return vault;
    }

    // ============================================================ Accesseurs

    public Settings settings() {
        return settings;
    }

    public ToolCatalog tools() {
        return tools;
    }

    public Messages msg() {
        return messages;
    }

    /** Explication des options (éditeur /krebbs config et LISEZMOI.txt). */
    public ConfigDocs configDocs() {
        return configDocs;
    }

    public Configuration config() {
        return config;
    }

    public Configuration menusConfig() {
        return menusConfig;
    }

    /** Fichiers de configuration, dans l'ordre de l'éditeur. */
    public List<JsonFile> configFiles() {
        return Arrays.asList(configFile, toolsFile, messagesFile, menusFile);
    }

    public ToolManager toolManager() {
        return toolManager;
    }

    public MenuManager menus() {
        return menus;
    }

    public CommandManager commands() {
        return commands;
    }

    /** Pont vers les GUI custom KoriaMC. */
    public GuiHook gui() {
        return gui;
    }
}
