package eu.endermite.commandwhitelist.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static eu.endermite.commandwhitelist.common.CommandFilter.Verdict.ALLOWED;
import static eu.endermite.commandwhitelist.common.CommandFilter.Verdict.COMMAND_DENIED;
import static eu.endermite.commandwhitelist.common.CommandFilter.Verdict.SUBCOMMAND_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAndFilterTest {

    @TempDir
    File dir;

    private final List<String> warnings = new ArrayList<>();
    private final CWLogger logger = new CWLogger() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    };

    private File file() {
        return new File(dir, "config.yml");
    }

    private void write(String... lines) throws IOException {
        Files.write(file().toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private String read() throws IOException {
        return new String(Files.readAllBytes(file().toPath()), StandardCharsets.UTF_8);
    }

    private static final Predicate<String> NOBODY = permission -> false;

    private ConfigCache twoGroups(String mode) throws IOException {
        write("mode:", "  commands: " + mode, "groups:",
                "  default:", "    commands: [spawn, '/help', tp]", "    subcommands: []",
                "  vip:", "    commands: [fly]", "    subcommands: ['fly fast']", "    custom_command_denied_message: 'vip only'");
        return new ConfigCache(file(), true, logger);
    }

    @Test
    void newFileHasDefaultGroup() throws IOException {
        ConfigCache config = new ConfigCache(file(), true, logger);
        assertTrue(config.get().groups.containsKey("default"), config.get().groups.keySet() + " " + warnings + "\n" + read());
        assertTrue(config.get().groups.get("default").getCommands().contains("spawn"));
    }

    @Test
    void userGroupsAreMerged() throws IOException {
        Settings settings = twoGroups("WHITELIST").get();
        Settings.Rules basic = settings.rulesFor(NOBODY);
        Settings.Rules vip = settings.rulesFor("commandwhitelist.group.vip"::equals);
        assertFalse(basic.commands.contains("fly"));
        assertTrue(vip.commands.contains("fly"));
        assertTrue(vip.commands.contains("spawn"));
    }

    @Test
    void permissionChangeIsSeenAtOnce() throws IOException {
        Settings settings = twoGroups("WHITELIST").get();
        assertEquals(COMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/fly", CommandFilter.AliasResolver.NONE));
        assertEquals(ALLOWED, CommandFilter.evaluate(settings, "commandwhitelist.group.vip"::equals, "/fly", CommandFilter.AliasResolver.NONE));
        assertEquals(COMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/fly", CommandFilter.AliasResolver.NONE));
    }

    @Test
    void entryWithSlashWorks() throws IOException {
        assertTrue(twoGroups("WHITELIST").get().groups.get("default").getCommands().contains("help"));
    }

    @Test
    void reloadRemovesDeletedGroup() throws IOException {
        ConfigCache config = twoGroups("WHITELIST");
        assertTrue(config.get().groups.containsKey("vip"));
        write("mode:", "  commands: WHITELIST", "groups:", "  default:", "    commands: [spawn]", "    subcommands: []");
        assertTrue(config.reloadConfig());
        assertFalse(config.get().groups.containsKey("vip"));
    }

    @Test
    void deletedDefaultGroupDoesNotComeBack() throws IOException {
        write("mode:", "  commands: WHITELIST", "groups:", "  staff:", "    commands: [ban]", "    subcommands: []");
        ConfigCache config = new ConfigCache(file(), true, logger);
        assertFalse(config.get().groups.containsKey("default"));
        assertTrue(config.reloadConfig());
        assertFalse(config.get().groups.containsKey("default"));
    }

    @Test
    void brokenFileKeepsPreviousConfiguration() throws IOException {
        ConfigCache config = twoGroups("WHITELIST");
        Settings before = config.get();
        write("groups:", "  default: [", "  : :");
        assertFalse(config.reloadConfig());
        assertEquals(before, config.get());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void brokenFileAtStartHasNoNullMessages() throws IOException {
        write("groups:", "  default: [", "  : :");
        Settings settings = new ConfigCache(file(), true, logger).get();
        assertEquals("<gold>CommandWhitelist <dark_gray>> <reset>", settings.prefix);
        assertEquals("<red>No such command.", settings.commandDenied);
        assertEquals(COMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/anything", CommandFilter.AliasResolver.NONE));
    }

    @Test
    void savingKeepsCustomMessageAndOrder() throws IOException {
        ConfigCache config = twoGroups("WHITELIST");
        assertTrue(config.editGroup("vip", group -> group.withCommands(Arrays.asList("fly", "heal"))));
        String saved = read();
        assertTrue(saved.contains("vip only"), saved);
        assertTrue(saved.indexOf("fly") < saved.indexOf("heal"), saved);
        assertTrue(config.get().groups.get("vip").getCommands().contains("heal"));
        assertTrue(config.reloadConfig());
        assertEquals("vip only", config.get().groups.get("vip").getCommandDeniedMessage());
    }

    @Test
    void editingUnknownGroupFails() throws IOException {
        assertFalse(twoGroups("WHITELIST").editGroup("nope", group -> group));
    }

    @Test
    void oldMatchTypeKeyStillWorks() throws IOException {
        write("mode:", "  matchtype: REGEX", "groups:", "  default:", "    commands: ['sp.*']", "    subcommands: []");
        ConfigCache config = new ConfigCache(file(), true, logger);
        assertEquals(MatchType.REGEX, config.get().matchType);
        assertFalse(read().contains("match_type"));
    }

    @Test
    void noMessageTypeWarningOnPlatformWithoutMessages() {
        new ConfigCache(file(), false, logger);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void protocolLibOptionOnlyWarns() throws IOException {
        write("use_protocollib: true", "groups:", "  default:", "    commands: [spawn]", "    subcommands: []");
        new ConfigCache(file(), true, logger);
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("use_protocollib")));
    }

    @Test
    void invalidEntriesAreRejected() {
        assertTrue(ConfigCache.commandEntryError("(", MatchType.REGEX) != null);
        assertTrue(ConfigCache.commandEntryError("two words", MatchType.LITERAL) != null);
        assertTrue(ConfigCache.subcommandEntryError("one", MatchType.LITERAL) != null);
        assertEquals("spawn", ConfigCache.normalizeCommand(" /spawn ", MatchType.LITERAL));
    }

    @Test
    void aliasesOfBlacklistedCommandAreBlocked() throws IOException {
        Settings settings = twoGroups("BLACKLIST").get();
        CommandFilter.AliasResolver aliases = label -> label.equals("teleport") ? Collections.singletonList("tp") : Collections.<String>emptyList();
        assertEquals(COMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/tp x", aliases));
        assertEquals(COMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/teleport x", aliases));
        assertEquals(ALLOWED, CommandFilter.evaluate(settings, NOBODY, "/kick x", aliases));
    }

    @Test
    void executeRunIsCheckedLikeTheCommandItRuns() throws IOException {
        Settings settings = twoGroups("BLACKLIST").get();
        assertEquals(COMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/execute as @s run tp 0 0 0", CommandFilter.AliasResolver.NONE));
        assertEquals(COMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/minecraft:execute run minecraft:tp 0 0 0", CommandFilter.AliasResolver.NONE));
        assertEquals(ALLOWED, CommandFilter.evaluate(settings, NOBODY, "/execute as @s run kick x", CommandFilter.AliasResolver.NONE));
    }

    @Test
    void subcommandThroughExecuteIsDenied() throws IOException {
        write("mode:", "  commands: BLACKLIST", "groups:", "  default:", "    commands: []", "    subcommands: ['gamemode creative']");
        Settings settings = new ConfigCache(file(), true, logger).get();
        assertEquals(SUBCOMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/execute run minecraft:gamemode creative", CommandFilter.AliasResolver.NONE));
        assertEquals(ALLOWED, CommandFilter.evaluate(settings, NOBODY, "/execute run gamemode survival", CommandFilter.AliasResolver.NONE));
    }

    @Test
    void hiddenEntriesCanBeUsedButAreNotSuggested() throws IOException {
        write("mode:", "  commands: WHITELIST", "  subcommands: WHITELIST", "groups:", "  default:",
                "    commands: [parkour, spawn]", "    subcommands: ['parkour list']", "    hidden: ['parkour prova', gui]");
        Settings settings = new ConfigCache(file(), true, logger).get();
        Settings.Rules rules = settings.rulesFor(NOBODY);
        CommandFilter.AliasResolver none = CommandFilter.AliasResolver.NONE;

        // executed from a menu: allowed although the whitelists do not list it
        assertEquals(ALLOWED, CommandFilter.evaluate(settings, NOBODY, "/parkour prova", none));
        assertEquals(ALLOWED, CommandFilter.evaluate(settings, NOBODY, "/parkour prova now", none));
        assertEquals(ALLOWED, CommandFilter.evaluate(settings, NOBODY, "/gui open", none));
        assertEquals(ALLOWED, CommandFilter.evaluate(settings, NOBODY, "/parkour list", none));
        // anything else of the command stays restricted
        assertEquals(SUBCOMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/parkour other", none));
        assertEquals(COMMAND_DENIED, CommandFilter.evaluate(settings, NOBODY, "/guix", none));

        // never suggested
        assertEquals(Arrays.asList("list"), CommandFilter.filterSuggestions(settings, rules, "/parkour ", Arrays.asList("list", "prova")));
        assertFalse(CommandFilter.isVisible(settings, NOBODY, "gui", none));
        assertTrue(CommandFilter.isVisible(settings, NOBODY, "parkour", none));
        assertFalse(CommandFilter.isBranchVisible(settings, rules, "parkour prova", none));
        assertTrue(CommandFilter.isBranchVisible(settings, rules, "parkour list", none));
    }

    @Test
    void hiddenKeyIsOptional() throws IOException {
        assertTrue(twoGroups("WHITELIST").get().groups.get("default").getHidden().isEmpty());
        assertFalse(twoGroups("WHITELIST").get().groups.get("default").serialize().containsKey("hidden"));
    }

    @Test
    void messageComesFromTheGroupThatCausedTheDenial() throws IOException {
        Settings whitelist = twoGroups("WHITELIST").get();
        assertEquals("vip only", whitelist.commandDeniedMessage("fly", NOBODY));
        assertEquals("<red>No such command.", whitelist.commandDeniedMessage("ban", NOBODY));
        Settings blacklist = twoGroups("BLACKLIST").get();
        // a blacklist denies through the groups the player has, not the ones the player is missing
        assertEquals("<red>No such command.", blacklist.commandDeniedMessage("fly", NOBODY));
        assertEquals("vip only", blacklist.commandDeniedMessage("fly", "commandwhitelist.group.vip"::equals));
    }

    @Test
    void oldPlaceholdersKeepWorking() {
        assertEquals("<command> for <group>", CWMessages.legacyPlaceholders("%s for %s"));
    }

    @Test
    void legacyColorCodesBecomeMiniMessage() {
        assertEquals("<red>no <bold>x<reset>", CWMessages.toMiniMessage("&cno &lx&r"));
        assertEquals("<#ff8800>hex", CWMessages.toMiniMessage("&#ff8800hex"));
        assertEquals("<gold>sect", CWMessages.toMiniMessage("§6sect"));
        assertEquals("Tom & Jerry <red>ok", CWMessages.toMiniMessage("Tom & Jerry <red>ok"));
    }
}
