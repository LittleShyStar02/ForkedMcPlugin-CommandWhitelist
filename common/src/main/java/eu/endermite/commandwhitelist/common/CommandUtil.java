package eu.endermite.commandwhitelist.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Comparison of command lines with the listed entries.
 * <p>
 * Case rules: a WHITELIST compares exactly, so /Ban is not the listed "ban" and gets denied. A BLACKLIST ignores
 * the case, because platforms may run /BAN as ban and a differently written command must not get through.
 * Either way the namespace of a command does not change which rules apply to it.
 */
public final class CommandUtil {

    /**
     * Upper bound of characters read by one regular expression match. Command lines are written by players,
     * so a pattern with catastrophic backtracking must not be able to freeze the server.
     * When the bound is exceeded the command is denied.
     */
    private static final int REGEX_STEP_LIMIT = 100_000;
    private static final Pattern NEVER_MATCHES = Pattern.compile("(?!)");
    private static final Map<String, Pattern> EXACT_PATTERNS = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> IGNORE_CASE_PATTERNS = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED_PATTERNS = ConcurrentHashMap.newKeySet();
    private static volatile Consumer<String> warner = message -> {
    };

    private CommandUtil() {
    }

    /**
     * @param warner Receives a warning, once for every pattern that had to be stopped
     */
    public static void setWarner(Consumer<String> warner) {
        CommandUtil.warner = warner;
    }

    /**
     * Forgets compiled patterns of a previous configuration.
     */
    public static void clearPatternCache() {
        EXACT_PATTERNS.clear();
        IGNORE_CASE_PATTERNS.clear();
        REPORTED_PATTERNS.clear();
    }

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
            boolean listed = isCommandListed(mode, matchType, listedCommands, label);

            if (mode == ListMode.WHITELIST) return listed;
            if (listed) return false;

            String withoutNamespace = stripNamespace(label);
            return withoutNamespace.equals(label) || !isCommandListed(mode, matchType, listedCommands, withoutNamespace);
        } catch (RegexBudgetExceededException e) {
            return false;
        }
    }

    /**
     * @param mode           Decides how the case is compared, see the class description
     * @param matchType      How the listed commands are compared with the label
     * @param listedCommands Commands listed in the groups
     * @param label          Command label, without the slash
     * @return True if the label is matched by any of the listed commands (namespace is not ignored)
     */
    public static boolean isCommandListed(ListMode mode, MatchType matchType, Collection<String> listedCommands, String label) {
        if (matchType == MatchType.LITERAL) {
            if (listedCommands.contains(label)) return true;
            if (mode == ListMode.WHITELIST) return false;

            for (String listed : listedCommands) {
                if (listed.equalsIgnoreCase(label)) return true;
            }

            return false;
        }

        for (String regex : listedCommands) {
            if (matches(compile(regex, mode), regex, label)) return true;
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

        List<String> plainLine = withoutNamespace(line);

        if (matchType == MatchType.REGEX)
            return isSubcommandAllowedRegex(mode, listedSubCommands, line, plainLine);

        boolean restricted = false;

        for (String listed : listedSubCommands) {
            List<String> entry = tokenize(listed);

            if (entry.isEmpty()) continue;

            if (mode == ListMode.BLACKLIST) {
                if (startsWithIgnoreCase(line, entry) || startsWithIgnoreCase(plainLine, entry)) return false;
                continue;
            }

            // the case does not decide if a command is restricted, otherwise /LP would escape the rules of lp
            if (!entry.get(0).equalsIgnoreCase(line.get(0)) && !entry.get(0).equalsIgnoreCase(plainLine.get(0))) continue;

            if (startsWithTokens(line, entry) || startsWithTokens(plainLine, entry)) return true;

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
        List<String> suggestionsList = new ArrayList<>(suggestions);

        if (suggestions.isEmpty() || subCommands.isEmpty()) return suggestionsList;

        if (buffer.startsWith("/"))
            buffer = buffer.substring(1);

        int lastSpace = buffer.lastIndexOf(' ');

        if (lastSpace < 0) return suggestionsList;

        // words that are already completed, the one currently being typed is not included
        List<String> typed = withoutNamespace(tokenize(buffer.substring(0, lastSpace)));

        if (typed.isEmpty()) return suggestionsList;

        if (matchType == MatchType.REGEX)
            return filterSuggestionsRegex(typed, suggestionsList, subCommands, mode);

        typed = lowerCase(typed);

        if (mode == ListMode.BLACKLIST) {
            Set<String> denied = new HashSet<>();

            for (String s : subCommands) {
                List<String> entry = lowerCase(withoutNamespace(tokenize(s)));

                if (entry.size() == typed.size() + 1 && startsWithTokens(entry, typed))
                    denied.add(entry.get(typed.size()));
            }

            suggestionsList.removeIf(suggestion -> denied.contains(suggestion.toLowerCase(Locale.ROOT)));
            return suggestionsList;
        }

        boolean restricted = false;
        Set<String> allowedNext = new HashSet<>();

        for (String listed : subCommands) {
            List<String> entry = lowerCase(withoutNamespace(tokenize(listed)));

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

    /**
     * @return True if the branch of a command tree should be sent to the player. Literal branches of a command
     * are suggested by the client itself, so the listed subcommands have to be removed from the tree too.
     */
    public static boolean isBranchVisible(ListMode mode, MatchType matchType, Collection<String> subCommands, String path) {
        if (mode == ListMode.WHITELIST) {
            List<String> filtered = filterSuggestions(cutLastArgument(path), Collections.singletonList(getLastArgument(path)), subCommands, mode, matchType);
            return !filtered.isEmpty();
        }
        return isSubcommandAllowed(mode, matchType, subCommands, path);
    }

    /**
     * Checks if a command line is covered by an entry of the hidden lists. Such a line can be used even if the
     * other lists would deny it, but it is never suggested. An entry is a command ("spawn") or a command with
     * subcommands ("parkour prova"), and it also covers the line when further arguments follow it.
     *
     * @param matchType   How the hidden entries are compared with the command line
     * @param hidden      Hidden entries of the groups available to the player
     * @param commandLine Full command line, with or without the slash
     * @return True if the command line can be used
     */
    public static boolean isHiddenListed(MatchType matchType, Collection<String> hidden, String commandLine) {
        if (hidden.isEmpty()) return false;

        List<String> line = tokenize(commandLine);

        if (line.isEmpty()) return false;

        List<String> plainLine = withoutNamespace(line);

        if (matchType == MatchType.REGEX) {
            String joinedLine = String.join(" ", line);
            String joinedPlainLine = String.join(" ", plainLine);
            try {
                for (String regex : hidden) {
                    Pattern pattern = subcommandPattern(regex, ListMode.BLACKLIST);
                    if (matches(pattern, regex, joinedLine) || matches(pattern, regex, joinedPlainLine)) return true;
                }
            } catch (RegexBudgetExceededException e) {
                return false;
            }

            return false;
        }

        for (String listed : hidden) {
            List<String> entry = tokenize(listed);

            if (entry.isEmpty()) continue;

            if (startsWithIgnoreCase(line, entry) || startsWithIgnoreCase(plainLine, entry)) return true;
        }

        return false;
    }

    /**
     * @return True if the label is a hidden command of its own (an entry of a single word), which must not be suggested
     */
    public static boolean isHiddenLabel(MatchType matchType, Collection<String> hidden, String label) {
        if (hidden.isEmpty()) return false;

        String plain = stripNamespace(label);

        try {
            for (String listed : hidden) {
                if (matchType == MatchType.REGEX) {
                    if (firstWordOf(listed).length() == listed.length()
                            && (matches(compile(listed, ListMode.BLACKLIST), listed, label)
                            || matches(compile(listed, ListMode.BLACKLIST), listed, plain))) return true;
                    continue;
                }

                List<String> entry = tokenize(listed);

                if (entry.size() == 1 && (entry.get(0).equalsIgnoreCase(label) || entry.get(0).equalsIgnoreCase(plain)))
                    return true;
            }
        } catch (RegexBudgetExceededException e) {
            return false;
        }

        return false;
    }

    private static boolean isSubcommandAllowedRegex(ListMode mode, Collection<String> listedSubCommands, List<String> line, List<String> plainLine) {
        String joinedLine = String.join(" ", line);
        String joinedPlainLine = String.join(" ", plainLine);
        try {
            boolean restricted = false;

            for (String regex : listedSubCommands) {
                Pattern pattern = subcommandPattern(regex, mode);

                if (mode == ListMode.BLACKLIST) {
                    if (matches(pattern, regex, joinedLine) || matches(pattern, regex, joinedPlainLine)) return false;
                    continue;
                }

                if (!appliesToCommand(regex, line.get(0)) && !appliesToCommand(regex, plainLine.get(0))) continue;

                if (matches(pattern, regex, joinedLine) || matches(pattern, regex, joinedPlainLine)) return true;

                restricted = true;
            }

            return mode == ListMode.BLACKLIST || !restricted;
        } catch (RegexBudgetExceededException e) {
            return false;
        }
    }

    private static List<String> filterSuggestionsRegex(List<String> typed, List<String> suggestionsList, Collection<String> subCommands, ListMode mode) {
        String completed = String.join(" ", typed) + " ";
        try {
            if (mode == ListMode.BLACKLIST) {
                suggestionsList.removeIf(suggestion -> {
                    for (String regex : subCommands) {
                        if (matches(subcommandPattern(regex, mode), regex, completed + suggestion)) return true;
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
                for (String regex : subCommands) {
                    if (!appliesToCommand(regex, typed.get(0))) continue;

                    // the candidate is either allowed already or can still become allowed when typed further
                    Matcher matcher = subcommandPattern(regex, mode).matcher(new BudgetedInput(completed + suggestion));

                    try {
                        if (matcher.matches() || matcher.hitEnd()) return false;
                    } catch (StackOverflowError e) {
                        throw stopped(regex);
                    } catch (RegexBudgetExceededException e) {
                        throw stopped(regex);
                    }
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
    private static Pattern compile(String regex, ListMode mode) {
        if (mode == ListMode.WHITELIST)
            return EXACT_PATTERNS.computeIfAbsent(regex, source -> compileOrNever(source, 0));
        return IGNORE_CASE_PATTERNS.computeIfAbsent(regex, source ->
                compileOrNever(source, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    private static Pattern compileOrNever(String source, int flags) {
        try {
            return Pattern.compile(source, flags);
        } catch (PatternSyntaxException e) {
            return NEVER_MATCHES;
        }
    }

    /**
     * @return Pattern that matches a command line that starts with the subcommand regex,
     * on the border of a word, so that "gamemode creative" also matches "gamemode creative Steve"
     */
    private static Pattern subcommandPattern(String regex, ListMode mode) {
        return compile("(?:" + regex + ")(?:\\s.*)?", mode);
    }

    private static boolean matches(Pattern pattern, String regex, CharSequence input) {
        try {
            return pattern.matcher(new BudgetedInput(input)).matches();
        } catch (StackOverflowError | RegexBudgetExceededException e) {
            throw stopped(regex);
        }
    }

    private static RegexBudgetExceededException stopped(String regex) {
        if (REPORTED_PATTERNS.add(regex))
            warner.accept("Regular expression \"" + regex + "\" took too long and was stopped, the command was denied. Consider simplifying it.");
        return new RegexBudgetExceededException();
    }

    /**
     * @return True if the first word of the subcommand regex matches the command label, in any case
     */
    private static boolean appliesToCommand(String subcommandRegex, String label) {
        String commandRegex = firstWordOf(subcommandRegex);
        return commandRegex.length() < subcommandRegex.length() && matches(compile(commandRegex, ListMode.BLACKLIST), commandRegex, label);
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
     * @return Words of the command line as typed, without the slash and without empty words
     */
    public static List<String> tokenize(String commandLine) {
        if (commandLine.startsWith("/"))
            commandLine = commandLine.substring(1);

        List<String> tokens = new ArrayList<>();

        for (String token : commandLine.split("\\s+")) {
            if (!token.isEmpty())
                tokens.add(token);
        }

        return tokens;
    }

    /**
     * @return The label without its namespace, "minecraft:tp" becomes "tp"
     */
    public static String stripNamespace(String label) {
        return label.substring(label.lastIndexOf(':') + 1);
    }

    private static List<String> withoutNamespace(List<String> tokens) {
        if (tokens.isEmpty() || tokens.get(0).indexOf(':') < 0) return tokens;
        List<String> plain = new ArrayList<>(tokens);
        plain.set(0, stripNamespace(tokens.get(0)));

        return plain;
    }

    private static List<String> lowerCase(List<String> tokens) {
        List<String> lower = new ArrayList<>(tokens.size());

        for (String token : tokens)
            lower.add(token.toLowerCase(Locale.ROOT));

        return lower;
    }

    /**
     * @return True if the tokens start with all the prefix tokens. An empty prefix never matches.
     */
    private static boolean startsWithTokens(List<String> tokens, List<String> prefix) {
        if (prefix.isEmpty() || prefix.size() > tokens.size()) return false;
        return tokens.subList(0, prefix.size()).equals(prefix);
    }

    private static boolean startsWithIgnoreCase(List<String> tokens, List<String> prefix) {
        if (prefix.isEmpty() || prefix.size() > tokens.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!tokens.get(i).equalsIgnoreCase(prefix.get(i))) return false;
        }

        return true;
    }

    /**
     * @param cmd The command
     * @return Last argument of the command
     */
    public static String getLastArgument(String cmd) {
        List<String> parts = tokenize(cmd);
        return parts.isEmpty() ? "" : parts.get(parts.size() - 1);
    }

    /**
     * @param cmd The command
     * @return Command without the last argument, with a trailing space
     */
    public static String cutLastArgument(String cmd) {
        List<String> parts = tokenize(cmd);
        if (parts.size() < 2) return "";
        return String.join(" ", parts.subList(0, parts.size() - 1)) + " ";
    }

    /**
     * @param cmd The command
     * @return Command label, empty if the line has no words
     */
    public static String getCommandLabel(String cmd) {
        List<String> parts = tokenize(cmd);
        return parts.isEmpty() ? "" : parts.get(0);
    }
}
