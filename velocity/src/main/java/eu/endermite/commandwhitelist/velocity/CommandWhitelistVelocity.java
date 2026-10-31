package eu.endermite.commandwhitelist.velocity;

import com.google.inject.Inject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.endermite.commandwhitelist.common.CWLogger;
import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandFilter;
import eu.endermite.commandwhitelist.common.CommandUtil;
import eu.endermite.commandwhitelist.common.ConfigCache;
import eu.endermite.commandwhitelist.common.ListMode;
import eu.endermite.commandwhitelist.common.MatchType;
import eu.endermite.commandwhitelist.common.CWMessages;
import eu.endermite.commandwhitelist.common.Messenger;
import eu.endermite.commandwhitelist.common.Settings;
import eu.endermite.commandwhitelist.common.commands.CWCommand;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bstats.charts.SimplePie;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CommandWhitelistVelocity implements CWCommand.Platform {

    private final ProxyServer server;
    private final Path folder;
    private final Logger logger;
    private final Metrics.Factory metricsFactory;
    private ConfigCache configCache;

    @Inject
    public CommandWhitelistVelocity(ProxyServer server, Logger logger, @DataDirectory final Path folder, Metrics.Factory metricsFactory) {
        this.server = server;
        this.folder = folder;
        this.logger = logger;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        configCache = new ConfigCache(folder.resolve("config.yml").toFile(), false, new CWLogger() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warn(message);
            }
        });

        server.getCommandManager().register(server.getCommandManager().metaBuilder("vcw").plugin(this).build(), new MainCommand());

        Metrics metrics = metricsFactory.make(this, 8704);
        metrics.addCustomChart(new SimplePie("proxy", () -> "Velocity"));
    }

    @Subscribe(order = PostOrder.LAST)
    @SuppressWarnings("UnstableApiUsage")
    public void onUserCommandSendEvent(PlayerAvailableCommandsEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission(CWPermission.BYPASS.permission())) return;

        Settings settings = configCache.get();

        int before = event.getRootNode().getChildren().size();

        event.getRootNode().getChildren().removeIf(commandNode ->
                server.getCommandManager().hasCommand(commandNode.getName())
                        && !CommandFilter.isVisible(settings, player::hasPermission, commandNode.getName(), CommandFilter.AliasResolver.NONE));

        // the client suggests literal branches of a command by itself, so the subcommands have to leave the tree too
        Settings.Rules rules = settings.rulesFor(player::hasPermission);

        configCache.debug("Command list of " + player.getUsername() + ": " + event.getRootNode().getChildren().size() + " of " + before + " commands kept");

        if (rules.subCommands.isEmpty() && rules.hidden.isEmpty()) return;

        for (CommandNode<?> root : event.getRootNode().getChildren()) {
            boolean listed = settings.debug && rules.subCommands.stream()
                    .anyMatch(entry -> CommandUtil.getCommandLabel(entry).equalsIgnoreCase(CommandUtil.stripNamespace(root.getName())));

            if (listed) logger.info("Command tree of " + player.getUsername() + ": /" + root.getName()
                    + " redirect=" + (root.getRedirect() != null) + " before=" + describe(root));

            pruneSubcommands(root, root.getName(), settings, rules);
            listedSubcommandsOnly(root, settings, rules);

            if (listed) logger.info("Command tree of " + player.getUsername() + ": /" + root.getName() + " after=" + describe(root));
        }
    }

    /**
     * A command of a plugin is often a single free text argument that the proxy completes by itself, and for modern
     * clients no event lets us filter what it suggests. In the WHITELIST mode the argument is replaced by the listed
     * subcommands, written as literal branches, and the original argument is kept after each of them.
     */
    @SuppressWarnings("unchecked")
    private void listedSubcommandsOnly(CommandNode<?> root, Settings settings, Settings.Rules rules) {
        if (settings.subcommandsMode != ListMode.WHITELIST || settings.matchType != MatchType.LITERAL) return;

        CommandNode<CommandSource> node = (CommandNode<CommandSource>) root;
        List<List<String>> entries = new ArrayList<>();

        for (String listed : rules.subCommands) {
            List<String> words = CommandUtil.tokenize(listed);

            if (words.size() > 1 && words.get(0).equalsIgnoreCase(CommandUtil.stripNamespace(root.getName())))
                entries.add(words);
        }

        if (entries.isEmpty()) return;

        List<CommandNode<CommandSource>> arguments = new ArrayList<>();

        for (CommandNode<CommandSource> child : node.getChildren()) {
            // a command that already has literal branches is handled by pruning them
            if (child instanceof LiteralCommandNode) return;

            arguments.add(child);
        }

        if (arguments.isEmpty()) return;

        node.getChildren().removeIf(child -> !(child instanceof LiteralCommandNode));

        for (List<String> words : entries) {
            CommandNode<CommandSource> parent = node;

            for (String word : words.subList(1, words.size())) {
                CommandNode<CommandSource> next = parent.getChild(word);

                if (next == null) {
                    next = LiteralArgumentBuilder.<CommandSource>literal(word).executes(context -> 0).build();
                    parent.addChild(next);
                }

                parent = next;
            }

            if (parent.getChildren().isEmpty()) {
                for (CommandNode<CommandSource> argument : arguments) parent.addChild(argument);
            }
        }
    }

    private static String describe(CommandNode<?> node) {
        StringBuilder out = new StringBuilder("[");
        for (CommandNode<?> child : node.getChildren())
            out.append(child.getName()).append(':').append(child.getClass().getSimpleName()).append(' ');

        return out.append(']').toString();
    }

    private void pruneSubcommands(CommandNode<?> node, String path, Settings settings, Settings.Rules rules) {
        node.getChildren().removeIf(child -> child instanceof LiteralCommandNode
                && !CommandFilter.isBranchVisible(settings, rules, path + " " + child.getName(), CommandFilter.AliasResolver.NONE));

        for (CommandNode<?> child : node.getChildren()) {
            if (child instanceof LiteralCommandNode)
                pruneSubcommands(child, path + " " + child.getName(), settings, rules);
        }
    }

    @Subscribe
    public void onUserCommandExecuteEvent(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;

        Player player = (Player) event.getCommandSource();

        if (player.hasPermission(CWPermission.BYPASS.permission())) return;

        // Workaround for velocity executing "/ command" as valid command
        String command = event.getCommand().trim();
        if (command.startsWith("/")) command = command.substring(1).trim();

        Settings settings = configCache.get();
        Settings.Rules rules = settings.rulesFor(player::hasPermission);
        String label = CommandUtil.getCommandLabel(command);

        configCache.debug(player.getUsername() + " ran \"" + command + "\": command "
                + CommandFilter.commandVerdict(settings, rules, command, CommandFilter.AliasResolver.NONE) + ", subcommand "
                + CommandFilter.subcommandVerdict(settings, rules, command, CommandFilter.AliasResolver.NONE)
                + (server.getCommandManager().hasCommand(label) ? "" : " (not a proxy command)"));

        if (server.getCommandManager().hasCommand(label)
                && CommandFilter.commandVerdict(settings, rules, command, CommandFilter.AliasResolver.NONE) != CommandFilter.Verdict.ALLOWED) {
            // the proxy pretends not to have the command, so the backend gives the usual answer
            event.setResult(CommandExecuteEvent.CommandResult.forwardToServer());
            return;
        }

        if (CommandFilter.subcommandVerdict(settings, rules, command, CommandFilter.AliasResolver.NONE) != CommandFilter.Verdict.ALLOWED) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            CommandFilter.notifyDenied(settings, new VelocityMessenger(player), player::hasPermission, CommandFilter.Verdict.SUBCOMMAND_DENIED, command);
        }
    }

    /**
     * THIS IS FOR CLIENTS ON 1.12 AND BELOW, NOT GUARANTEED TO WORK, IF IT DOESN'T, PR OF GTFO
     */
    @Subscribe
    public void onUserTabCompleteEvent(TabCompleteEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission(CWPermission.BYPASS.permission())) return;
        if (event.getSuggestions().isEmpty()) return;

        Settings settings = configCache.get();

        List<String> newSuggestions = CommandFilter.filterSuggestions(
                settings,
                settings.rulesFor(player::hasPermission),
                event.getPartialMessage(),
                event.getSuggestions()
        );

        configCache.debug("Tab completion of " + player.getUsername() + " for \"" + event.getPartialMessage() + "\": "
                + newSuggestions.size() + " of " + event.getSuggestions().size() + " suggestions kept");

        event.getSuggestions().clear();
        event.getSuggestions().addAll(newSuggestions);
    }

    @Override
    public ConfigCache config() {
        return configCache;
    }

    @Override
    public void reload(Consumer<Boolean> whenDone) {
        server.getScheduler().buildTask(this, () -> whenDone.accept(configCache.reloadConfig())).schedule();
    }

    @Override
    public Collection<String> serverCommands() {
        return new ArrayList<>(server.getCommandManager().getAliases());
    }

    @Override
    public File dumpFile() {
        return folder.resolve("command_dump.yml").toFile();
    }

    private final class MainCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            CWCommand.execute(CommandWhitelistVelocity.this, new VelocityMessenger(source), source::hasPermission, invocation.alias(), invocation.arguments());
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return CWCommand.suggestions(CommandWhitelistVelocity.this, invocation.source()::hasPermission, invocation.arguments());
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().getPermissionValue("commandwhitelist.command") != Tristate.FALSE;
        }
    }

    /**
     * Velocity brings its own Adventure, so this class does not use the copy bundled for the other platforms.
     */
    private static final class VelocityMessenger implements Messenger {

        private final Audience audience;

        private VelocityMessenger(Audience audience) {
            this.audience = audience;
        }

        private static Component parse(String miniMessage, Map<String, String> placeholders) {
            List<TagResolver> resolvers = new ArrayList<>();
            for (Map.Entry<String, String> placeholder : placeholders.entrySet())
                resolvers.add(Placeholder.unparsed(placeholder.getKey(), placeholder.getValue()));

            return MiniMessage.miniMessage().deserialize(CWMessages.toMiniMessage(miniMessage), TagResolver.resolver(resolvers));
        }

        @Override
        public void chat(String miniMessage, Map<String, String> placeholders) {
            audience.sendMessage(parse(miniMessage, placeholders));
        }

        @Override
        public void actionBar(String miniMessage, Map<String, String> placeholders) {
            audience.sendActionBar(parse(miniMessage, placeholders));
        }

        @Override
        public void sound(String key, float volume, float pitch) {
            audience.playSound(Sound.sound(Key.key(key), Sound.Source.UI, volume, pitch));
        }
    }
}
