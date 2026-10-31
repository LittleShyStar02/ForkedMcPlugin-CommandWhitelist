package eu.endermite.commandwhitelist.bukkit;

import eu.endermite.commandwhitelist.bukkit.listeners.CommandSendListener;
import eu.endermite.commandwhitelist.bukkit.listeners.ExecuteListener;
import eu.endermite.commandwhitelist.bukkit.listeners.TabCompleteListener;
import eu.endermite.commandwhitelist.common.CWLogger;
import eu.endermite.commandwhitelist.common.CommandFilter;
import eu.endermite.commandwhitelist.common.ConfigCache;
import eu.endermite.commandwhitelist.common.Messenger;
import eu.endermite.commandwhitelist.common.commands.CWCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpTopic;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public class CommandWhitelistBukkit extends JavaPlugin implements CWCommand.Platform, TabExecutor {

    private ConfigCache configCache;
    private BukkitAudiences audiences;
    private PlayerScheduler scheduler;
    private CommandMap commandMap;
    private Map<String, Command> knownCommands;
    private volatile AliasIndex aliasIndex;
    private volatile Function<String, Collection<String>> extraAliases = label -> Collections.emptyList();

    /**
     * Other names of the command a label resolves to. The resolver of the whole server, used by all the listeners.
     */
    public final CommandFilter.AliasResolver aliases = this::otherNames;

    @Override
    public void onEnable() {
        audiences = BukkitAudiences.create(this);
        configCache = new ConfigCache(new File(getDataFolder(), "config.yml"), true, new CWLogger() {
            @Override
            public void info(String message) {
                getLogger().info(message);
            }

            @Override
            public void warn(String message) {
                getLogger().warning(message);
            }
        });
        readCommandMap();
        scheduler = createScheduler();

        getServer().getPluginManager().registerEvents(new ExecuteListener(this), this);
        getServer().getPluginManager().registerEvents(new TabCompleteListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandSendListener(this), this);
        enablePaperFeatures();

        PluginCommand command = getCommand("commandwhitelist");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        try {
            new Metrics(this, 8705);
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(Level.FINE, "Metrics are not available", e);
        }
    }

    @Override
    public void onDisable() {
        if (audiences != null) audiences.close();
    }

    private PlayerScheduler createScheduler() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
        } catch (ClassNotFoundException e) {
            return new PlayerScheduler.Standard(this);
        }
        try {
            return (PlayerScheduler) Class.forName("eu.endermite.commandwhitelist.folia.FoliaPlayerScheduler")
                    .getConstructor(org.bukkit.plugin.Plugin.class).newInstance(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Folia support could not be loaded", e);
        }
    }

    /**
     * The features of Paper live in their own module, so that the plugin can be loaded on servers without them.
     */
    private void enablePaperFeatures() {
        try {
            Class.forName("com.destroystokyo.paper.event.server.AsyncTabCompleteEvent");
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            Class.forName("eu.endermite.commandwhitelist.paper.PaperFeatures")
                    .getMethod("enable", CommandWhitelistBukkit.class).invoke(null, this);
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().log(Level.WARNING, "Paper features could not be enabled, using the regular ones", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void readCommandMap() {
        try {
            commandMap = (CommandMap) getServer().getClass().getMethod("getCommandMap").invoke(getServer());
            Class<?> type = commandMap.getClass();
            while (type != null && knownCommands == null) {
                try {
                    Field field = type.getDeclaredField("knownCommands");
                    field.setAccessible(true);
                    knownCommands = (Map<String, Command>) field.get(commandMap);
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().warning("Could not read the commands of the server, aliases of blacklisted commands will not be recognized: " + e);
        }
    }

    /**
     * @param extraAliases Other names of a command known only to the running server software
     */
    public void setExtraAliases(Function<String, Collection<String>> extraAliases) {
        this.extraAliases = extraAliases;
    }

    private Collection<String> otherNames(String label) {
        Set<String> names = new LinkedHashSet<>(extraAliases.apply(label));
        Map<String, Command> known = knownCommands;
        if (known != null) {
            String lowerLabel = label.toLowerCase(Locale.ROOT);
            addNames(names, known.get(lowerLabel));
            addNames(names, commandsByAlias(known).get(lowerLabel));
        }
        names.remove(label);
        return names;
    }

    private static void addNames(Set<String> names, Command command) {
        if (command == null) return;
        names.add(command.getName());
        names.addAll(command.getAliases());
    }

    /**
     * On some servers an alias is not registered as the command that declares it, so the lookup goes both ways.
     * Rebuilt only when the amount of commands changes, because it runs for every command in the command tree.
     */
    private Map<String, Command> commandsByAlias(Map<String, Command> known) {
        AliasIndex index = aliasIndex;
        int size = known.size();
        if (index != null && index.size == size) return index.commands;
        Map<String, Command> commands = new HashMap<>();
        for (Command command : new ArrayList<>(known.values())) {
            for (String alias : command.getAliases())
                commands.putIfAbsent(alias.toLowerCase(Locale.ROOT), command);
        }
        aliasIndex = new AliasIndex(size, commands);
        return commands;
    }

    private static final class AliasIndex {
        private final int size;
        private final Map<String, Command> commands;

        private AliasIndex(int size, Map<String, Command> commands) {
            this.size = size;
            this.commands = commands;
        }
    }

    public ConfigCache getConfigCache() {
        return configCache;
    }

    public PlayerScheduler getPlayerScheduler() {
        return scheduler;
    }

    public Messenger messenger(CommandSender sender) {
        return new BukkitMessenger(audiences.sender(sender));
    }

    @Override
    public ConfigCache config() {
        return configCache;
    }

    @Override
    public void reload(Consumer<Boolean> whenDone) {
        scheduler.async(() -> {
            boolean success = configCache.reloadConfig();
            for (Player player : new ArrayList<>(getServer().getOnlinePlayers())) {
                scheduler.onPlayerThread(player, player::updateCommands);
            }
            whenDone.accept(success);
        });
    }

    @Override
    public Collection<String> serverCommands() {
        if (knownCommands != null) return new ArrayList<>(knownCommands.keySet());
        Set<String> commands = new LinkedHashSet<>();
        for (HelpTopic topic : getServer().getHelpMap().getHelpTopics()) {
            String name = topic.getName();
            if (Character.isUpperCase(name.charAt(0))) continue;
            commands.add(name);
        }
        return commands;
    }

    @Override
    public File dumpFile() {
        return new File(getDataFolder(), "command_dump.yml");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CWCommand.execute(this, messenger(sender), sender::hasPermission, label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return CWCommand.suggestions(this, sender::hasPermission, args);
    }
}
