package eu.endermite.commandwhitelist.common;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CommandUtil {

    /**
     * Upper bound of characters read by one regular expression match. Command lines are written by players,
     * so a pattern with catastrophic backtracking must not be able to freeze the server.
     * When the bound is exceeded the command is denied.
     */
    private static final int REGEX_STEP_LIMIT = 100_000;
    private static final Pattern NEVER_MATCHES = Pattern.compile("(?!)");
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * Checks if a command label is allowed to be used, according to the commands mode.
     * In the BLACKLIST mode the namespace of the label (for example "minecraft:" in "minecraft:tp")
     * is ignored, otherwise every blacklisted command could be bypassed by prefixing it with a namespace.
     * With the REGEX match type every listed entry is a regular expression that has to match the whole label.
     *
     * @param mode           Mode in which the listed commands are interpreted
     * @param matchType      How the listed commands are compared with the label
     * @param listedCommands Commands listed in the groups available to the player
     * @param label          Command label, without the slash
     * @return True if the command is allowed
     */
    public static boolean isCommandAllowed(ListMode mode, MatchType matchType, Collection<String> listedCommands, String label) {
        try {
            boolean listed = isCommandListed(matchType, listedCommands, label);
            if (mode == ListMode.WHITELIST) return listed;
            if (listed) return false;
            int namespaceEnd = label.lastIndexOf(':');
            return namespaceEnd < 0 || !isCommandListed(matchType, listedCommands, label.substring(namespaceEnd + 1));
        } catch (RegexBudgetExceededException e) {
            return false;
        }
    }

    /**
     * @param matchType      How the listed commands are compared with the label
     * @param listedCommands Commands listed in the groups
     * @param label          Command label, without the slash
     * @return True if the label is matched by any of the listed commands (namespace is not ignored)
     */
    public static boolean isCommandListed(MatchType matchType, Collection<String> listedCommands, String label) {
        if (matchType == MatchType.LITERAL)
            return listedCommands.contains(label.toLowerCase(Locale.ROOT));
        for (String regex : listedCommands) {
            if (matchesRegex(regex, label)) return true;
        }
        return false;
    }

    /**
     * Checks if a full command line is allowed to be used, according to the subcommands mode.
     * <ul>
     *     <li>BLACKLIST - denied if the command line starts with any of the listed subcommands</li>
     *     <li>WHITELIST - a command that has at least one listed subcommand can only be used with one of
     *     the listed subcommands. Commands without any listed subcommand are not restricted.</li>
     * </ul>
     * With the REGEX match type every listed subcommand is a regular expression matched against the whole
     * command line (words separated by single spaces, no slash). Like a literal subcommand it also matches
     * when further arguments follow it. In the WHITELIST mode the part of the expression before its first
     * space decides to which commands the entry applies.
     *
     * @param mode              Mode in which the listed subcommands are interpreted
     * @param matchType         How the listed subcommands are compared with the command line
     * @param listedSubCommands Subcommands listed in the groups available to the player
     * @param commandLine       Full command line, with or without the slash
     * @return True if the command line is allowed
     */
    public static boolean isSubcommandAllowed(ListMode mode, MatchType matchType, Collection<String> listedSubCommands, String commandLine) {
        List<String> line = tokenize(commandLine);
        if (line.isEmpty()) return true;
        // a command used without any argument does not use a subcommand
        if (mode == ListMode.WHITELIST && line.size() < 2) return true;
        if (matchType == MatchType.REGEX)
            return isSubcommandAllowedRegex(mode, listedSubCommands, line);
        boolean restricted = false;
        for (String listed : listedSubCommands) {
            List<String> entry = tokenize(listed);
            if (mode == ListMode.BLACKLIST) {
                if (startsWithTokens(line, entry)) return false;
                continue;
            }
            if (entry.isEmpty() || !entry.get(0).equals(line.get(0))) continue;
            if (startsWithTokens(line, entry)) return true;
            restricted = true;
        }
        return mode == ListMode.BLACKLIST || !restricted;
    }

    /**
     * Filters suggestions that lead to a subcommand that is not allowed
     * from provided collection of strings, according to the subcommands mode.
     *
     * @param buffer      Command buffer
     * @param suggestions Full suggestions list
     * @param subCommands Subcommands listed in the groups available to the player
     * @param mode        Mode in which the listed subcommands are interpreted
     * @param matchType   How the listed subcommands are compared with the command line
     * @return Filtered list of suggestions
     */
    public static List<String> filterSuggestions(String buffer, Collection<String> suggestions, Collection<String> subCommands, ListMode mode, MatchType matchType) {
        if (buffer.startsWith("/"))
            buffer = buffer.substring(1);
        List<String> suggestionsList = new ArrayList<>(suggestions);
        if (suggestions.isEmpty() || subCommands.isEmpty()) return suggestionsList;
        if (matchType == MatchType.REGEX)
            return filterSuggestionsRegex(buffer, suggestionsList, subCommands, mode);
        if (mode == ListMode.BLACKLIST) {
            for (String s : subCommands) {
                String scommand = cutLastArgument(s);
                if (buffer.startsWith(scommand)) {
                    String slast = getLastArgument(s);
                    while (suggestionsList.contains(slast))
                        suggestionsList.remove(slast);
                }
            }
            return suggestionsList;
        }

        int lastSpace = buffer.lastIndexOf(' ');
        if (lastSpace < 0) return suggestionsList;
        // words that are already completed, the one currently being typed is not included
        List<String> typed = tokenize(buffer.substring(0, lastSpace));
        if (typed.isEmpty()) return suggestionsList;

        boolean restricted = false;
        Set<String> allowedNext = new HashSet<>();
        for (String listed : subCommands) {
            List<String> entry = tokenize(listed);
            if (entry.isEmpty() || !entry.get(0).equals(typed.get(0))) continue;
            // a whitelisted subcommand is already fully typed, the rest is not restricted
            if (startsWithTokens(typed, entry)) return suggestionsList;
            restricted = true;
            if (entry.size() > typed.size() && startsWithTokens(entry, typed))
                allowedNext.add(entry.get(typed.size()));
        }
        if (!restricted) return suggestionsList;
        suggestionsList.removeIf(suggestion -> !allowedNext.contains(suggestion.toLowerCase(Locale.ROOT)));
        return suggestionsList;
    }

    private static boolean isSubcommandAllowedRegex(ListMode mode, Collection<String> listedSubCommands, List<String> line) {
        String joinedLine = String.join(" ", line);
        try {
            boolean restricted = false;
            for (String regex : listedSubCommands) {
                if (mode == ListMode.BLACKLIST) {
                    if (matchesSubcommandRegex(regex, joinedLine)) return false;
                    continue;
                }
                if (!appliesToCommand(regex, line.get(0))) continue;
                if (matchesSubcommandRegex(regex, joinedLine)) return true;
                restricted = true;
            }
            return mode == ListMode.BLACKLIST || !restricted;
        } catch (RegexBudgetExceededException e) {
            return false;
        }
    }

    private static List<String> filterSuggestionsRegex(String buffer, List<String> suggestionsList, Collection<String> subCommands, ListMode mode) {
        int lastSpace = buffer.lastIndexOf(' ');
        if (lastSpace < 0) return suggestionsList;
        // words that are already completed, the one currently being typed is not included
        List<String> typed = tokenize(buffer.substring(0, lastSpace));
        if (typed.isEmpty()) return suggestionsList;
        String completed = String.join(" ", typed) + " ";
        try {
            if (mode == ListMode.BLACKLIST) {
                suggestionsList.removeIf(suggestion -> {
                    String candidate = completed + suggestion.toLowerCase(Locale.ROOT);
                    for (String regex : subCommands) {
                        if (matchesSubcommandRegex(regex, candidate)) return true;
                    }
                    return false;
                });
                return suggestionsList;
            }
            boolean restricted = false;
            for (String regex : subCommands) {
                if (appliesToCommand(regex, typed.get(0))) {
                    restricted = true;
                    break;
                }
            }
            if (!restricted) return suggestionsList;
            suggestionsList.removeIf(suggestion -> {
                String candidate = completed + suggestion.toLowerCase(Locale.ROOT);
                for (String regex : subCommands) {
                    if (!appliesToCommand(regex, typed.get(0))) continue;
                    // the candidate is either allowed already or can still become allowed when typed further
                    Matcher matcher = subcommandPattern(regex).matcher(new BudgetedInput(candidate));
                    if (matcher.matches() || matcher.hitEnd()) return false;
                }
                return true;
            });
            return suggestionsList;
        } catch (RegexBudgetExceededException e) {
            return new ArrayList<>();
        }
    }

    /**
     * @return Pattern of a listed command or subcommand. A pattern that does not compile never matches.
     */
    private static Pattern compile(String regex) {
        return PATTERN_CACHE.computeIfAbsent(regex, source -> {
            try {
                return Pattern.compile(source, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (PatternSyntaxException e) {
                return NEVER_MATCHES;
            }
        });
    }

    /**
     * @return Pattern that matches a command line that starts with the subcommand regex,
     * on the border of a word, so that "gamemode creative" also matches "gamemode creative Steve"
     */
    private static Pattern subcommandPattern(String regex) {
        return compile("(?:" + regex + ")(?:\\s.*)?");
    }

    private static boolean matchesRegex(String regex, CharSequence input) {
        return compile(regex).matcher(new BudgetedInput(input)).matches();
    }

    private static boolean matchesSubcommandRegex(String regex, String commandLine) {
        return subcommandPattern(regex).matcher(new BudgetedInput(commandLine)).matches();
    }

    /**
     * @return True if the first word of the subcommand regex matches the command label
     */
    private static boolean appliesToCommand(String subcommandRegex, String label) {
        String commandRegex = firstWordOf(subcommandRegex);
        return commandRegex.length() < subcommandRegex.length() && matchesRegex(commandRegex, label);
    }

    /**
     * @return Part of the regex before the first space, which is not inside a group or a character class.
     * Returns the whole regex when it has no such space.
     */
    static String firstWordOf(String regex) {
        int groupDepth = 0;
        boolean inClass = false;
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            if (c == '\\') {
                if (!inClass && groupDepth == 0 && i + 1 < regex.length()
                        && (regex.charAt(i + 1) == 's' || regex.charAt(i + 1) == ' '))
                    return regex.substring(0, i);
                i++;
            } else if (inClass) {
                if (c == ']') inClass = false;
            } else if (c == '[') {
                inClass = true;
            } else if (c == '(') {
                groupDepth++;
            } else if (c == ')') {
                groupDepth--;
            } else if (groupDepth == 0 && Character.isWhitespace(c)) {
                return regex.substring(0, i);
            }
        }
        return regex;
    }

    /**
     * Checks that a regex can be used in the list of commands.
     *
     * @throws PatternSyntaxException when the regex is not valid
     */
    public static void validateCommandRegex(String regex) throws PatternSyntaxException {
        Pattern.compile(regex);
    }

    /**
     * Checks that a regex can be used in the list of subcommands.
     *
     * @throws PatternSyntaxException   when the regex is not valid, or its first word (the command) is not
     * @throws IllegalArgumentException when the regex has no space, so there is no command and subcommand in it
     */
    public static void validateSubcommandRegex(String regex) throws PatternSyntaxException {
        Pattern.compile(regex);
        String commandRegex = firstWordOf(regex);
        if (commandRegex.length() == regex.length())
            throw new IllegalArgumentException("no space between the command and the subcommand");
        Pattern.compile(commandRegex);
    }

    /**
     * Character sequence that gives up when too many characters were read.
     */
    private static final class BudgetedInput implements CharSequence {

        private final CharSequence input;
        private int reads = 0;

        private BudgetedInput(CharSequence input) {
            this.input = input;
        }

        @Override
        public int length() {
            return input.length();
        }

        @Override
        public char charAt(int index) {
            if (++reads > REGEX_STEP_LIMIT) throw new RegexBudgetExceededException();
            return input.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return input.subSequence(start, end);
        }

        @Override
        public String toString() {
            return input.toString();
        }
    }

    private static final class RegexBudgetExceededException extends RuntimeException {

        private RegexBudgetExceededException() {
            super("regex took too long", null, false, false);
        }
    }

    /**
     * @param commandLine Command line, with or without the slash
     * @return Lowercase words of the command line, without the slash and without empty words
     */
    private static List<String> tokenize(String commandLine) {
        if (commandLine.startsWith("/"))
            commandLine = commandLine.substring(1);
        List<String> tokens = new ArrayList<>();
        for (String token : commandLine.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!token.isEmpty())
                tokens.add(token);
        }
        return tokens;
    }

    /**
     * @return True if the tokens start with all the prefix tokens. An empty prefix never matches.
     */
    private static boolean startsWithTokens(List<String> tokens, List<String> prefix) {
        if (prefix.isEmpty() || prefix.size() > tokens.size()) return false;
        return tokens.subList(0, prefix.size()).equals(prefix);
    }

    /**
     * @param cmd The command
     * @return Last argument of the command
     */
    public static String getLastArgument(String cmd) {
        String[] parts = cmd.split(" ");
        if (parts.length == 0) return "";
        return parts[parts.length - 1];
    }

    /**
     * @param cmd The command
     * @return Command without the last argument.
     */
    public static String cutLastArgument(String cmd) {
        String[] cmdSplit = cmd.split(" ");
        StringBuilder cmdBuilder = new StringBuilder();
        for (int i = 0; i <= cmdSplit.length - 2; i++)
            cmdBuilder.append(cmdSplit[i]).append(" ");
        return cmdBuilder.toString();
    }

    /**
     * @param cmd The command
     * @return Command label
     */
    public static String getCommandLabel(String cmd) {
        String[] parts = cmd.split(" ");
        if (parts[0].startsWith("/"))
            parts[0] = parts[0].substring(1);
        return parts[0];
    }

    /**
     * Dumps command list to a file
     *
     * @param serverCommands Commands to dump
     * @return True on successful file save
     */
    public static boolean dumpAllBukkitCommands(ArrayList<String> serverCommands, File file) {
        try {
            File parent = new File(file.getParent());
            if (!parent.exists())
                parent.mkdir();
            if (!file.exists())
                file.createNewFile();
        } catch (IOException e) {
            return false;
        }


        try {
            ConfigFile dumpFile = ConfigFile.loadConfig(file);
            dumpFile.set("commands", serverCommands);
            dumpFile.save();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

}
