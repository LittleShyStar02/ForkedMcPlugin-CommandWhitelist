package eu.endermite.commandwhitelist.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The decisions shared by the listeners of all the platforms.
 */
public final class CommandFilter {

    public enum Verdict {
        ALLOWED, COMMAND_DENIED, SUBCOMMAND_DENIED
    }

    /**
     * Names of the command a label resolves to on the platform, the label itself not included.
     */
    public interface AliasResolver extends Function<String, Collection<String>> {
        AliasResolver NONE = label -> Collections.emptyList();
    }

    private CommandFilter() {
    }

    /**
     * @param settings    Active configuration
     * @param permissions Permission check of the player
     * @param commandLine Command line, with or without the slash
     * @param aliases     Other names of the same command
     * @return What should happen with the command
     */
    public static Verdict evaluate(Settings settings, Predicate<String> permissions, String commandLine, AliasResolver aliases) {
        Settings.Rules rules = settings.rulesFor(permissions);
        Verdict verdict = commandVerdict(settings, rules, commandLine, aliases);
        if (verdict != Verdict.ALLOWED) return verdict;
        return subcommandVerdict(settings, rules, commandLine, aliases);
    }

    /**
     * Checks only the command label. Used by proxies, which decide about the label only for their own commands.
     */
    public static Verdict commandVerdict(Settings settings, Settings.Rules rules, String commandLine, AliasResolver aliases) {
        List<String> tokens = CommandUtil.tokenize(commandLine);
        String label = tokens.isEmpty() ? "" : tokens.get(0);
        if (!isLabelAllowed(settings, rules, label, aliases)) return Verdict.COMMAND_DENIED;
        // the command after "execute ... run" is what actually gets executed
        List<String> target = executeTarget(tokens);
        if (target != null) return evaluateTarget(settings, rules, target, aliases);
        return Verdict.ALLOWED;
    }

    public static Verdict subcommandVerdict(Settings settings, Settings.Rules rules, String commandLine, AliasResolver aliases) {
        List<String> tokens = CommandUtil.tokenize(commandLine);
        if (tokens.isEmpty()) return Verdict.ALLOWED;
        if (!CommandUtil.isSubcommandAllowed(settings.subcommandsMode, settings.matchType, rules.subCommands, commandLine))
            return Verdict.SUBCOMMAND_DENIED;
        for (String alias : aliases.apply(CommandUtil.stripNamespace(tokens.get(0)))) {
            List<String> sameCommand = new ArrayList<>(tokens);
            sameCommand.set(0, alias);
            if (!CommandUtil.isSubcommandAllowed(settings.subcommandsMode, settings.matchType, rules.subCommands, String.join(" ", sameCommand)))
                return Verdict.SUBCOMMAND_DENIED;
        }
        return Verdict.ALLOWED;
    }

    private static Verdict evaluateTarget(Settings settings, Settings.Rules rules, List<String> target, AliasResolver aliases) {
        String line = String.join(" ", target);
        Verdict verdict = commandVerdict(settings, rules, line, aliases);
        return verdict != Verdict.ALLOWED ? verdict : subcommandVerdict(settings, rules, line, aliases);
    }

    /**
     * @return Words after the "run" of an execute command, null if the line is not such a command
     */
    private static List<String> executeTarget(List<String> tokens) {
        if (tokens.isEmpty() || !CommandUtil.stripNamespace(tokens.get(0)).equalsIgnoreCase("execute")) return null;
        for (int i = 1; i < tokens.size() - 1; i++) {
            if (tokens.get(i).equalsIgnoreCase("run")) return tokens.subList(i + 1, tokens.size());
        }
        return null;
    }

    /**
     * @return True if the label can be used. In the BLACKLIST mode all the names of the command count, because
     * blocking a command must also block its aliases. A WHITELIST allows only what is written in it.
     */
    public static boolean isLabelAllowed(Settings settings, Settings.Rules rules, String label, AliasResolver aliases) {
        if (!CommandUtil.isCommandAllowed(settings.commandsMode, settings.matchType, rules.commands, label)) return false;
        if (settings.commandsMode == ListMode.WHITELIST) return true;
        for (String alias : aliases.apply(CommandUtil.stripNamespace(label))) {
            if (!CommandUtil.isCommandAllowed(settings.commandsMode, settings.matchType, rules.commands, alias)) return false;
        }
        return true;
    }

    /**
     * Applies the configured reaction to a denied command.
     *
     * @param verdict     Result of {@link #evaluate}, not ALLOWED
     * @param commandLine Denied command line
     */
    public static void notifyDenied(Settings settings, Messenger messenger, Predicate<String> permissions, Verdict verdict, String commandLine) {
        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        String message = verdict == Verdict.SUBCOMMAND_DENIED
                ? settings.subcommandDenied
                : settings.commandDeniedMessage(CommandUtil.getCommandLabel(line), permissions);
        CWMessages.denied(settings, messenger, settings.prefix + message, line);
    }

    /**
     * @return Filtered suggestions, null if the whole completion has to be cancelled
     */
    public static List<String> filterTabCompletion(Settings settings, Predicate<String> permissions, String buffer, Collection<String> completions, AliasResolver aliases) {
        int commandEnd = buffer.indexOf(' ');
        Settings.Rules rules = settings.rulesFor(permissions);
        if (commandEnd < 0 || buffer.charAt(0) != '/'
                || !isLabelAllowed(settings, rules, buffer.substring(1, commandEnd), aliases))
            return null;
        List<String> filtered = CommandUtil.filterSuggestions(buffer, completions, rules.subCommands, settings.subcommandsMode, settings.matchType);
        // the rules written for another name of the command apply to this name too, like when the command is run
        String rest = buffer.substring(commandEnd);
        for (String alias : aliases.apply(CommandUtil.stripNamespace(buffer.substring(1, commandEnd))))
            filtered = CommandUtil.filterSuggestions("/" + alias + rest, filtered, rules.subCommands, settings.subcommandsMode, settings.matchType);
        return filtered;
    }

    /**
     * @param path Words of a branch of the command tree, starting with the label of the command
     * @return True if the branch should be sent to the player under every name of the command
     */
    public static boolean isBranchVisible(Settings settings, Settings.Rules rules, String path, AliasResolver aliases) {
        if (!CommandUtil.isBranchVisible(settings.subcommandsMode, settings.matchType, rules.subCommands, path)) return false;
        List<String> tokens = CommandUtil.tokenize(path);
        if (tokens.isEmpty()) return true;
        for (String alias : aliases.apply(CommandUtil.stripNamespace(tokens.get(0)))) {
            List<String> sameCommand = new ArrayList<>(tokens);
            sameCommand.set(0, alias);
            if (!CommandUtil.isBranchVisible(settings.subcommandsMode, settings.matchType, rules.subCommands, String.join(" ", sameCommand)))
                return false;
        }
        return true;
    }

    /**
     * @return True if a command with this name should be shown in the command tree
     */
    public static boolean isVisible(Settings settings, Predicate<String> permissions, String name, AliasResolver aliases) {
        return isLabelAllowed(settings, settings.rulesFor(permissions), name, aliases);
    }
}
