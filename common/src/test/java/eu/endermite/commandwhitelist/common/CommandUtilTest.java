package eu.endermite.commandwhitelist.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static eu.endermite.commandwhitelist.common.ListMode.BLACKLIST;
import static eu.endermite.commandwhitelist.common.ListMode.WHITELIST;
import static eu.endermite.commandwhitelist.common.MatchType.LITERAL;
import static eu.endermite.commandwhitelist.common.MatchType.REGEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandUtilTest {

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    void whitelistComparesExactly() {
        assertTrue(CommandUtil.isCommandAllowed(WHITELIST, LITERAL, set("ban"), "ban"));
        assertFalse(CommandUtil.isCommandAllowed(WHITELIST, LITERAL, set("ban"), "Ban"));
        assertTrue(CommandUtil.isCommandAllowed(WHITELIST, LITERAL, set("Ban"), "Ban"));
        assertFalse(CommandUtil.isCommandAllowed(WHITELIST, LITERAL, set("Ban"), "ban"));
    }

    @Test
    void blacklistBlocksEveryWayOfWritingIt() {
        for (String label : Arrays.asList("ban", "Ban", "BAN", "minecraft:ban", "MINECRAFT:BAN"))
            assertFalse(CommandUtil.isCommandAllowed(BLACKLIST, LITERAL, set("ban"), label), label);
        assertFalse(CommandUtil.isCommandAllowed(BLACKLIST, LITERAL, set("Ban"), "ban"));
        assertTrue(CommandUtil.isCommandAllowed(BLACKLIST, LITERAL, set("ban"), "kick"));
    }

    @Test
    void whitelistDoesNotAllowNamespaceOfListedCommand() {
        assertFalse(CommandUtil.isCommandAllowed(WHITELIST, LITERAL, set("tp"), "minecraft:tp"));
        assertTrue(CommandUtil.isCommandAllowed(WHITELIST, LITERAL, set("minecraft:tp"), "minecraft:tp"));
    }

    @Test
    void regexCaseFollowsTheMode() {
        assertTrue(CommandUtil.isCommandAllowed(WHITELIST, REGEX, set("ciao\\w"), "ciao1"));
        assertFalse(CommandUtil.isCommandAllowed(WHITELIST, REGEX, set("ciao\\w"), "CIAO1"));
        assertFalse(CommandUtil.isCommandAllowed(BLACKLIST, REGEX, set("ciao\\w"), "CIAO1"));
        assertFalse(CommandUtil.isCommandAllowed(BLACKLIST, REGEX, set("ciao\\w"), "ns:ciao1"));
    }

    @Test
    void namespaceDoesNotBypassSubcommandBlacklist() {
        for (MatchType type : MatchType.values()) {
            assertFalse(CommandUtil.isSubcommandAllowed(BLACKLIST, type, set("gamemode creative"), "/gamemode creative"));
            assertFalse(CommandUtil.isSubcommandAllowed(BLACKLIST, type, set("gamemode creative"), "/minecraft:gamemode creative"));
            assertFalse(CommandUtil.isSubcommandAllowed(BLACKLIST, type, set("gamemode creative"), "/GAMEMODE  Creative Steve"));
            assertTrue(CommandUtil.isSubcommandAllowed(BLACKLIST, type, set("gamemode creative"), "/gamemode survival"));
        }
    }

    @Test
    void namespaceAndCaseDoNotBypassSubcommandWhitelist() {
        for (MatchType type : MatchType.values()) {
            assertTrue(CommandUtil.isSubcommandAllowed(WHITELIST, type, set("lp info"), "/lp info"));
            assertTrue(CommandUtil.isSubcommandAllowed(WHITELIST, type, set("lp info"), "/luckperms:lp info"));
            assertFalse(CommandUtil.isSubcommandAllowed(WHITELIST, type, set("lp info"), "/lp editor"));
            assertFalse(CommandUtil.isSubcommandAllowed(WHITELIST, type, set("lp info"), "/luckperms:lp editor"));
            assertFalse(CommandUtil.isSubcommandAllowed(WHITELIST, type, set("lp info"), "/LP editor"));
            assertFalse(CommandUtil.isSubcommandAllowed(WHITELIST, type, set("lp info"), "/lp Info"));
            assertTrue(CommandUtil.isSubcommandAllowed(WHITELIST, type, set("lp info"), "/spawn now"));
            assertTrue(CommandUtil.isSubcommandAllowed(WHITELIST, type, set("lp info"), "/lp"));
        }
    }

    @Test
    void regexEntryIsTakenAsWritten() {
        // what such an entry was meant to do cannot be known: it is not rewritten and it applies to "gm"
        assertTrue(CommandUtil.isSubcommandAllowed(WHITELIST, REGEX, set("gm info|lp info"), "/lp editor"));
    }

    @Test
    void tabFilterBlacklist() {
        List<String> all = Arrays.asList("about", "other");
        List<String> filtered = Collections.singletonList("other");
        for (MatchType type : MatchType.values()) {
            assertEquals(filtered, CommandUtil.filterSuggestions("/help ", all, set("help about"), BLACKLIST, type));
            assertEquals(filtered, CommandUtil.filterSuggestions("/HELP ", all, set("help about"), BLACKLIST, type));
            assertEquals(filtered, CommandUtil.filterSuggestions("/ns:help ", all, set("help about"), BLACKLIST, type));
            assertEquals(Collections.emptyList(), CommandUtil.filterSuggestions("/help ", Collections.singletonList("ABOUT"), set("help about"), BLACKLIST, type));
        }
        assertEquals(all, CommandUtil.filterSuggestions("/help x ", all, set("help about"), BLACKLIST, LITERAL));
    }

    @Test
    void tabFilterWhitelist() {
        List<String> all = Arrays.asList("info", "editor");
        assertEquals(Collections.singletonList("info"), CommandUtil.filterSuggestions("/lp ", all, set("lp info"), WHITELIST, LITERAL));
        assertEquals(Collections.singletonList("info"), CommandUtil.filterSuggestions("/lp ", all, set("lp info"), WHITELIST, REGEX));
        assertEquals(all, CommandUtil.filterSuggestions("/spawn ", all, set("lp info"), WHITELIST, LITERAL));
    }

    @Test
    void branchesOfTheCommandTree() {
        assertFalse(CommandUtil.isBranchVisible(BLACKLIST, LITERAL, set("gamemode creative"), "gamemode creative"));
        assertTrue(CommandUtil.isBranchVisible(BLACKLIST, LITERAL, set("gamemode creative"), "gamemode survival"));
        assertTrue(CommandUtil.isBranchVisible(WHITELIST, LITERAL, set("lp info"), "lp info"));
        assertFalse(CommandUtil.isBranchVisible(WHITELIST, LITERAL, set("lp info"), "lp editor"));
    }

    @Test
    void parserHandlesOddLines() {
        assertEquals("", CommandUtil.getCommandLabel(" "));
        assertEquals("", CommandUtil.getCommandLabel(""));
        assertEquals("tp", CommandUtil.getCommandLabel("/ tp"));
        assertEquals("Tp", CommandUtil.getCommandLabel("/Tp x"));
    }

    @Test
    void slowRegexIsStoppedAndReported() {
        Set<String> reported = new HashSet<>();
        CommandUtil.setWarner(reported::add);
        CommandUtil.clearPatternCache();
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 200; i++) input.append('a');
        input.append('!');
        String slow = "(.*)(.*)(.*)(.*)(.*)x";
        assertFalse(CommandUtil.isCommandAllowed(BLACKLIST, REGEX, set(slow), input.toString()));
        assertFalse(CommandUtil.isCommandAllowed(BLACKLIST, REGEX, set(slow), input.toString()));
        assertEquals(1, reported.size());
        CommandUtil.setWarner(message -> {
        });
    }

    @Test
    void stackOverflowDeniesTheCommand() {
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 50_000; i++) input.append('a');
        assertFalse(CommandUtil.isCommandAllowed(BLACKLIST, REGEX, set("(a|b)*"), input.toString()));
    }
}
