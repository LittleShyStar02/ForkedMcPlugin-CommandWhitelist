package eu.endermite.commandwhitelist.waterfall;

import eu.endermite.commandwhitelist.bukkit.BukkitMessenger;
import eu.endermite.commandwhitelist.common.CWLogger;
import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandFilter;
import eu.endermite.commandwhitelist.common.ConfigCache;
import eu.endermite.commandwhitelist.common.Messenger;
import eu.endermite.commandwhitelist.common.Settings;
import eu.endermite.commandwhitelist.common.commands.CWCommand;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;
import org.bstats.bungeecord.Metrics;
import org.bstats.charts.SimplePie;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class CommandWhitelistWaterfall extends Plugin implements CWCommand.Platform, Listener {

    private ConfigCache configCache;
    private BungeeAudiences audiences;
    private final CommandFilter.AliasResolver aliases = this::otherNames;

    @Override
    public void onEnable() {
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
        audiences = BungeeAudiences.create(this);
        Metrics metrics = new Metrics(this, 8704);

        getProxy().getPluginManager().registerListener(this, this);
        try {
            Class.forName("io.github.waterfallmc.waterfall.event.ProxyDefineCommandsEvent");
            metrics.addCustomChart(new SimplePie("proxy", () -> "Waterfall"));
            getProxy().getPluginManager().registerListener(this, new WaterfallDefineCommandsListener(this));
        } catch (ClassNotFoundException e) {
            metrics.addCustomChart(new SimplePie("proxy", () -> "Bungee"));
            getLogger().severe("Hiding commands from the command list requires Waterfall or a fork of it.");
        }
        getProxy().getPluginManager().registerCommand(this, new MainCommand());
    }

    @Override
    public void onDisable() {
        if (audiences != null) audiences.close();
    }

    ConfigCache getConfigCache() {
        return configCache;
    }

    private Messenger messenger(CommandSender sender) {
        return new BukkitMessenger(audiences.sender(sender));
    }

    private Collection<String> otherNames(String label) {
        String lowerLabel = label.toLowerCase(Locale.ROOT);
        Command command = null;
        for (Map.Entry<String, Command> entry : getProxy().getPluginManager().getCommands()) {
            if (entry.getKey().equals(lowerLabel)) {
                command = entry.getValue();
                break;
            }
        }
        if (command == null) return Collections.emptyList();
        Set<String> names = new LinkedHashSet<>();
        names.add(command.getName());
        Collections.addAll(names, command.getAliases());
        names.remove(label);
        return names;
    }

    @EventHandler
    public void onChatEvent(ChatEvent event) {
        if (event.isCancelled() || !event.isProxyCommand() || !(event.getSender() instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (player.hasPermission(CWPermission.BYPASS.permission())) return;
        Settings settings = configCache.get();
        CommandFilter.Verdict verdict = CommandFilter.evaluate(settings, player::hasPermission, event.getMessage(), aliases);
        if (verdict == CommandFilter.Verdict.ALLOWED) return;
        event.setCancelled(true);
        CommandFilter.notifyDenied(settings, messenger(player), player::hasPermission, verdict, event.getMessage());
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (player.hasPermission(CWPermission.BYPASS.permission())) return;
        String cursor = event.getCursor();
        int commandEnd = cursor.indexOf(' ');
        // commands of the backend servers are not decided here
        boolean proxyCommand = commandEnd > 0 && cursor.charAt(0) == '/'
                && getProxy().getPluginManager().isExecutableCommand(cursor.substring(1, commandEnd).toLowerCase(Locale.ROOT), player);
        Settings settings = configCache.get();
        Settings.Rules rules = settings.rulesFor(player::hasPermission);
        if (!proxyCommand) {
            filter(event, eu.endermite.commandwhitelist.common.CommandUtil.filterSuggestions(cursor, event.getSuggestions(), rules.subCommands, settings.subcommandsMode, settings.matchType));
            return;
        }
        List<String> filtered = CommandFilter.filterTabCompletion(settings, player::hasPermission, cursor, event.getSuggestions(), aliases);
        if (filtered == null) event.setCancelled(true);
        else filter(event, filtered);
    }

    private static void filter(TabCompleteEvent event, List<String> filtered) {
        event.getSuggestions().clear();
        event.getSuggestions().addAll(filtered);
    }

    @Override
    public ConfigCache config() {
        return configCache;
    }

    @Override
    public void reload(Consumer<Boolean> whenDone) {
        getProxy().getScheduler().runAsync(this, () -> whenDone.accept(configCache.reloadConfig()));
    }

    @Override
    public Collection<String> serverCommands() {
        List<String> serverCommands = new ArrayList<>();
        for (Map.Entry<String, Command> command : getProxy().getPluginManager().getCommands())
            serverCommands.add(command.getKey());
        return serverCommands;
    }

    @Override
    public File dumpFile() {
        return new File(getDataFolder(), "command_dump.yml");
    }

    private final class MainCommand extends Command implements TabExecutor {

        private MainCommand() {
            super("bcw");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            CWCommand.execute(CommandWhitelistWaterfall.this, messenger(sender), sender::hasPermission, getName(), args);
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            return CWCommand.suggestions(CommandWhitelistWaterfall.this, sender::hasPermission, args);
        }
    }
}
