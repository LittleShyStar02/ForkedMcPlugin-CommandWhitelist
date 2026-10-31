package eu.endermite.commandwhitelist.common.commands;

import eu.endermite.commandwhitelist.common.CWGroup;
import eu.endermite.commandwhitelist.common.CWMessages;
import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandUtil;
import eu.endermite.commandwhitelist.common.ConfigCache;
import eu.endermite.commandwhitelist.common.Messenger;
import eu.endermite.commandwhitelist.common.Settings;
import io.github.thatsmusic99.configurationmaster.api.ConfigFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The /cw command, the same on every platform.
 */
public final class CWCommand {

    /**
     * What the command needs from the platform it is running on.
     */
    public interface Platform {
        ConfigCache config();

        /**
         * Runs the reload outside of the main thread and refreshes the commands of online players if needed.
         *
         * @param whenDone Receives the result of the reload
         */
        void reload(java.util.function.Consumer<Boolean> whenDone);

        Collection<String> serverCommands();

        File dumpFile();
    }

    private static final List<String> ADMIN_ACTIONS = Arrays.asList("add", "remove", "addsub", "removesub", "dump");

    private CWCommand() {
    }

    public static void execute(Platform platform, Messenger out, Predicate<String> permissions, String label, String[] args) {
        Settings settings = platform.config().get();
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        boolean reloadPerm = permissions.test(CWPermission.RELOAD.permission());
        boolean adminPerm = permissions.test(CWPermission.ADMIN.permission());

        if (action.equals("reload")) {
            if (!reloadPerm) {
                CWMessages.send(settings, out, settings.noPermission);
                return;
            }

            platform.reload(success -> {
                Settings current = platform.config().get();
                CWMessages.send(current, out, success ? current.configReloaded : current.configReloadFailed);
            });
            return;
        }

        if (!ADMIN_ACTIONS.contains(action)) {
            out.chat(help(label, reloadPerm, adminPerm), Collections.<String, String>emptyMap());
            return;
        }

        if (!adminPerm) {
            CWMessages.send(settings, out, settings.noPermission);
            return;
        }

        if (action.equals("dump")) {
            dump(platform, out);
            return;
        }

        if (args.length < 3) {
            out.chat("<red>Usage: <aqua>/" + label + " " + action + " \\<group> \\<" + (action.endsWith("sub") ? "subcommand" : "command") + ">", Collections.<String, String>emptyMap());
            return;
        }

        edit(platform, out, action, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
    }

    private static void edit(Platform platform, Messenger out, String action, String groupId, String entry) {
        ConfigCache config = platform.config();
        Settings settings = config.get();
        boolean subcommand = action.endsWith("sub");
        boolean add = action.startsWith("add");

        if (add) {
            String error = subcommand
                    ? ConfigCache.subcommandEntryError(entry, settings.matchType)
                    : ConfigCache.commandEntryError(entry, settings.matchType);

            if (error != null) {
                out.chat(settings.prefix + settings.invalidEntry, CWMessages.placeholders(entry + " (" + error + ")", groupId));
                return;
            }
        }

        String value = subcommand ? entry : ConfigCache.normalizeCommand(entry, settings.matchType);

        boolean done = config.editGroup(groupId, group -> {
            if (subcommand) return group.withSubCommands(changed(group.getSubCommands(), value, add));

            return group.withCommands(changed(group.getCommands(), value, add));
        });

        Settings current = config.get();
        String message = !done ? current.groupDoesntExist : add ? current.addedToWhitelist : current.removedFromWhitelist;
        out.chat(current.prefix + CWMessages.legacyPlaceholders(message), CWMessages.placeholders(value, groupId));
    }

    private static Set<String> changed(Set<String> entries, String entry, boolean add) {
        Set<String> result = new LinkedHashSet<>(entries);

        if (add) result.add(entry);
        else result.remove(entry);

        return result;
    }

    private static void dump(Platform platform, Messenger out) {
        out.chat("<yellow>Dumping all available commands to a file...", Collections.<String, String>emptyMap());
        boolean saved = dumpCommands(new ArrayList<>(platform.serverCommands()), platform.dumpFile(), platform.config());
        out.chat(saved ? "<green>Commands dumped to <gold>command_dump.yml" : "<red>Failed to save the file.", Collections.<String, String>emptyMap());
    }

    /**
     * @return True on successful file save
     */
    private static boolean dumpCommands(List<String> serverCommands, File file, ConfigCache config) {
        try {
            File parent = file.getParentFile();

            if (parent != null) parent.mkdirs();

            file.createNewFile();

            ConfigFile dumpFile = ConfigFile.loadConfig(file);
            dumpFile.set("commands", serverCommands);
            dumpFile.save();

            return true;
        } catch (Exception e) {
            config.warn("Could not save " + file + ": " + e);
            return false;
        }
    }

    static String help(String label, boolean reloadPerm, boolean adminPerm) {
        StringBuilder help = new StringBuilder("<rainbow><bold>CommandWhitelist by YouHaveTrouble</bold></rainbow>");
        line(help, label, "help", "Displays this message");

        if (reloadPerm) line(help, label, "reload", "Reloads plugin configuration");

        if (adminPerm) {
            line(help, label, "add \\<group> \\<command>", "Add a command to selected permission group");
            line(help, label, "remove \\<group> \\<command>", "Removes a command from selected permission group");
            line(help, label, "addsub \\<group> \\<subcommand>", "Add a subcommand to selected permission group");
            line(help, label, "removesub \\<group> \\<subcommand>", "Removes a subcommand from selected permission group");
            line(help, label, "dump", "Saves all the commands of the server to a file");
        }

        return help.toString();
    }

    private static void line(StringBuilder help, String label, String usage, String description) {
        help.append("<newline><aqua>/").append(label).append(' ').append(usage).append("</aqua><blue> - ").append(description).append("</blue>");
    }

    /**
     * @param args Arguments of the command. The last one is the one being typed and can be empty.
     */
    public static List<String> suggestions(Platform platform, Predicate<String> permissions, String[] args) {
        List<String> list = new ArrayList<>();
        Settings settings = platform.config().get();
        boolean reloadPerm = permissions.test(CWPermission.RELOAD.permission());
        boolean adminPerm = permissions.test(CWPermission.ADMIN.permission());
        String typed = args.length == 0 ? "" : args[args.length - 1];

        if (args.length <= 1) {
            add(list, "help", typed);

            if (reloadPerm) add(list, "reload", typed);

            if (adminPerm) {
                for (String action : ADMIN_ACTIONS) add(list, action, typed);
            }

            return list;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if (!adminPerm || !ADMIN_ACTIONS.contains(action) || action.equals("dump")) return list;

        if (args.length == 2) {
            for (String group : settings.groups.keySet()) add(list, group, typed);
            return list;
        }

        CWGroup group = settings.groups.get(args[1]);

        if (group == null || args.length > 3) return list;

        if (action.equals("remove")) {
            for (String command : group.getCommands()) add(list, command, typed);
        } else if (action.equals("removesub")) {
            for (String subCommand : group.getSubCommands()) add(list, subCommand, typed);
        } else if (action.equals("add")) {
            for (String command : platform.serverCommands()) {
                String plain = CommandUtil.stripNamespace(command.startsWith("/") ? command.substring(1) : command);
                if (!group.getCommands().contains(plain) && !list.contains(plain)) add(list, plain, typed);
            }
        }

        return list;
    }

    private static void add(List<String> list, String value, String typed) {
        if (value.startsWith(typed)) list.add(value);
    }
}
