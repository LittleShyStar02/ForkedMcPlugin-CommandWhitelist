package eu.endermite.commandwhitelist.common;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ConfigCache {

    private static final String DEFAULT_SOUND_KEY = "entity.villager.no";
    private static final Pattern SOUND_KEY = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9/._-]+");

    private final File configFile;
    private final boolean supportsCommandDeniedMessage;
    private final CWLogger logger;
    private ConfigFile config;
    private volatile Settings settings = new Settings(new Settings.Builder());

    /**
     * @param configFile                   File of the configuration
     * @param supportsCommandDeniedMessage False on a platform that lets the backend answer to denied commands
     * @param logger                       Output for warnings and debug messages
     */
    public ConfigCache(File configFile, boolean supportsCommandDeniedMessage, CWLogger logger) {
        this.configFile = configFile;
        this.supportsCommandDeniedMessage = supportsCommandDeniedMessage;
        this.logger = logger;
        CommandUtil.setWarner(logger::warn);
        reloadConfig();
    }

    public Settings get() {
        return settings;
    }

    public void warn(String log) {
        logger.warn(log);
    }

    public void debug(String log) {
        if (settings.debug) logger.info(log);
    }

    /**
     * @return True if the file was loaded. Otherwise the previous configuration stays active.
     */
    public synchronized boolean reloadConfig() {
        boolean newFile = configFile.length() == 0;
        ConfigFile loaded;
        try {
            createFiles();
            checkSyntax();
            loaded = ConfigFile.loadConfig(configFile);
        } catch (Exception e) {
            logger.warn("Could not load " + configFile + ": " + e);
            return false;
        }
        try {
            addDefaults(loaded, newFile);
            Settings.Builder values = read(loaded);
            config = loaded;
            CommandUtil.clearPatternCache();
            settings = new Settings(values);
        } catch (RuntimeException e) {
            logger.warn("Could not read " + configFile + ": " + e);
            return false;
        }
        return saveConfig();
    }

    /**
     * A file with a syntax error must be reported and left as it is, not replaced by the default values.
     */
    private void checkSyntax() throws java.io.IOException {
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            Object content = new org.yaml.snakeyaml.Yaml().load(reader);
            if (content != null && !(content instanceof Map))
                throw new IllegalArgumentException("the file is not a list of keys and values");
        }
    }

    private void addDefaults(ConfigFile config, boolean newFile) {
        config.addDefault("debug", false, "Writes to the console what the plugin decides, for finding why a command is or is not filtered.");

        config.addDefault("messages.prefix", "CommandWhitelist > ");
        if (supportsCommandDeniedMessage) {
            config.addDefault("messages.command_denied", "No such command.");
            config.addDefault("messages.subcommand_denied", "You cannot use this subcommand");
        }

        config.addDefault("messages.no_permission", "<red>You don't have permission to do this.");
        config.addDefault("messages.config_reloaded", "<yellow>Configuration reloaded.");
        config.addDefault("messages.config_reload_failed", "<red>Configuration could not be loaded, the previous one stays active. Check the console.");
        config.addDefault("messages.added_to_whitelist", "<yellow>Whitelisted command <gold><command> <yellow>for permission <gold><group>");
        config.addDefault("messages.removed_from_whitelist", "<yellow>Removed command <gold><command> <yellow>from permission <gold><group>");
        config.addDefault("messages.group_doesnt_exist", "<red>Group doesn't exist or error occured");
        config.addDefault("messages.invalid_entry", "<red>Not a valid entry: <gold><command>");

        config.addComment("messages", "Messages use MiniMessage formatting (https://docs.advntr.dev/minimessage/format.html)");

        config.addDefault("mode.commands", ListMode.WHITELIST.toString(), "How the \"commands\" lists of the groups are interpreted.\nWHITELIST - players can only see/use the listed commands, written exactly as listed\nBLACKLIST - players can see/use every command except the listed ones, however they are written");
        config.addDefault("mode.subcommands", ListMode.BLACKLIST.toString(), "How the \"subcommands\" lists of the groups are interpreted.\nBLACKLIST - players cannot see/use the listed subcommands\nWHITELIST - a command with listed subcommands can only be used with one of them\n(commands that have no listed subcommands are not restricted)");
        // the older spelling of the key keeps working and is not duplicated in the file
        if (config.getString("mode.matchtype") == null)
            config.addDefault("mode.match_type", MatchType.LITERAL.toString(), "How every entry of the \"commands\" and \"subcommands\" lists is compared with what the player typed.\nLITERAL (or NORMAL) - the entry is the exact command, for example \"tpa\" or \"help about\"\nREGEX - the entry is a regular expression, for example 'ciao\\w' matches ciao1, ciaoX...\nIn REGEX a command entry must match the whole command, a subcommand entry must match the beginning of the command line\n(words separated by one space, no slash), for example 'gamemode (creative|spectator)'.\nWith the WHITELIST mode the expression is case sensitive, with the BLACKLIST mode it is not.\nWith the WHITELIST mode for subcommands, the part of the entry before its first space says to which commands it applies.\nWrite regular expressions between single quotes in the groups, so that backslashes are kept as they are.");

        if (supportsCommandDeniedMessage) {
            config.addDefault("message_type", MessageType.CHAT.toString(), "Valid message types are CHAT and ACTIONBAR and CHAT_AND_ACTIONBAR.");
            config.addDefault("command_denied_sound.enabled", false);
            config.addDefault("command_denied_sound.key", DEFAULT_SOUND_KEY);
            config.addDefault("command_denied_sound.volume", 1.0f);
            config.addDefault("command_denied_sound.pitch", 1.0f);
        }

        if (newFile) {
            config.addExample("groups.example.commands", new ArrayList<>(Arrays.asList("example")), "These are the commands of the group \"example\". Depending on mode.commands, players will only be able to see/use them (WHITELIST) or will NOT be able to see/use them (BLACKLIST)");
            config.addExample("groups.example.subcommands", new ArrayList<>(Arrays.asList("example of")), "These are the subcommands of the group \"example\". Depending on mode.subcommands, players will NOT be able to see/use them (BLACKLIST) or will only be able to use these subcommands of their command (WHITELIST)");
            config.addExample("groups.example.custom_command_denied_message", "You don't have commandwhitelist.group.example permission.", "This is a custom message that players will see when this group is the reason of the denial.\nIf you don't want to use a custom message, set custom_command_denied_message: \"\"");
            config.addComment("groups.example", "All groups except from default require commandwhitelist.group.<group_name> permission\ncommandwhitelist.group.example in this case\n If you wish to leave the list empty, put \"commands: []\" or \"subcommands: []\"");
        }

        config.makeSectionLenient("groups");
        // only for a new file: a group deleted on purpose must not come back with its commands allowed
        if (newFile) {
            List<String> defaultCommands = new ArrayList<>(Arrays.asList("help", "spawn", "bal", "balance", "baltop", "pay", "r", "msg", "tpa", "tpahere", "tpaccept", "tpdeny", "warp"));
            List<String> defaultSubcommands = new ArrayList<>(Arrays.asList("help about"));
            config.addDefault("groups.default.commands", defaultCommands);
            config.addDefault("groups.default.subcommands", defaultSubcommands);
        }
    }

    private Settings.Builder read(ConfigFile config) {
        Settings.Builder b = new Settings.Builder();
        b.prefix = config.getString("messages.prefix", b.prefix);
        b.commandDenied = config.getString("messages.command_denied", b.commandDenied);
        b.subcommandDenied = config.getString("messages.subcommand_denied", b.subcommandDenied);
        b.noPermission = config.getString("messages.no_permission", b.noPermission);
        b.configReloaded = config.getString("messages.config_reloaded", b.configReloaded);
        b.configReloadFailed = config.getString("messages.config_reload_failed", b.configReloadFailed);
        b.addedToWhitelist = config.getString("messages.added_to_whitelist", b.addedToWhitelist);
        b.removedFromWhitelist = config.getString("messages.removed_from_whitelist", b.removedFromWhitelist);
        b.groupDoesntExist = config.getString("messages.group_doesnt_exist", b.groupDoesntExist);
        b.invalidEntry = config.getString("messages.invalid_entry", b.invalidEntry);
        if (config.getBoolean("use_protocollib", false))
            logger.warn("use_protocollib is not supported anymore and has no effect, commands are always filtered with the regular listeners.");
        b.debug = config.getBoolean("debug", false);
        b.commandsMode = loadListMode(config, "mode.commands", ListMode.WHITELIST);
        b.subcommandsMode = loadListMode(config, "mode.subcommands", ListMode.BLACKLIST);
        b.matchType = loadMatchType(config);
        if (supportsCommandDeniedMessage) {
            b.messageType = loadMessageType(config);
            readSound(config, b);
        }
        ConfigSection groupSection = config.getConfigSection("groups");
        if (groupSection != null) {
            for (String key : groupSection.getKeys(false)) {
                b.groups.put(key, loadCWGroup(key, groupSection, b.matchType));
            }
        }
        return b;
    }

    private void readSound(ConfigFile config, Settings.Builder b) {
        b.soundEnabled = config.getBoolean("command_denied_sound.enabled", false);
        if (!b.soundEnabled) return;
        String key = config.getString("command_denied_sound.key");
        if (key == null || !SOUND_KEY.matcher(key.toLowerCase(Locale.ROOT)).matches()) {
            logger.warn("Invalid sound key. Using " + DEFAULT_SOUND_KEY);
            key = DEFAULT_SOUND_KEY;
        }
        b.soundKey = key.toLowerCase(Locale.ROOT);
        b.soundVolume = config.getFloat("command_denied_sound.volume");
        b.soundPitch = config.getFloat("command_denied_sound.pitch");
    }

    private MessageType loadMessageType(ConfigFile config) {
        String value = config.getString("message_type");
        try {
            if (value != null) return MessageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        logger.warn("Invalid message type. Using CHAT.");
        return MessageType.CHAT;
    }

    private ListMode loadListMode(ConfigFile config, String path, ListMode fallback) {
        String value = config.getString(path);
        if (value == null) {
            logger.warn("Invalid " + path + ". Using " + fallback + ".");
            return fallback;
        }
        try {
            return ListMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid " + path + " \"" + value + "\", valid modes are WHITELIST and BLACKLIST. Using " + fallback + ".");
            return fallback;
        }
    }

    private MatchType loadMatchType(ConfigFile config) {
        String value = config.getString("mode.match_type");
        if (value == null) value = config.getString("mode.matchtype");
        if (value == null) {
            logger.warn("Invalid mode.match_type. Using " + MatchType.LITERAL + ".");
            return MatchType.LITERAL;
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.equals("NORMAL")) return MatchType.LITERAL;
        try {
            return MatchType.valueOf(value);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid mode.match_type \"" + value + "\", valid match types are LITERAL (or NORMAL) and REGEX. Using " + MatchType.LITERAL + ".");
            return MatchType.LITERAL;
        }
    }

    private boolean saveConfig() {
        try {
            config.save();
            return true;
        } catch (Exception e) {
            logger.warn("Could not save " + configFile + ": " + e);
            return false;
        }
    }

    private void createFiles() throws java.io.IOException {
        File parent = configFile.getParentFile();
        if (parent != null) parent.mkdirs();
        configFile.createNewFile();
    }

    private CWGroup loadCWGroup(String id, ConfigSection section, MatchType matchType) {
        Set<String> commands = new LinkedHashSet<>();
        for (String cmd : section.getStringList(id + ".commands")) {
            String error = commandEntryError(cmd, matchType);
            if (error != null) {
                logger.warn("\"" + cmd + "\" " + error + ". Skipping it.");
                continue;
            }
            commands.add(normalizeCommand(cmd, matchType));
        }
        Set<String> subCommands = new LinkedHashSet<>();
        for (String subCmd : section.getStringList(id + ".subcommands")) {
            String error = subcommandEntryError(subCmd, matchType);
            if (error != null) {
                logger.warn("\"" + subCmd + "\" " + error + ". Skipping it.");
                continue;
            }
            subCommands.add(subCmd);
        }
        return new CWGroup(id, commands, subCommands, section.getString(id + ".custom_command_denied_message"));
    }

    /**
     * @return The form of a command entry that is compared with the commands of players
     */
    public static String normalizeCommand(String command, MatchType matchType) {
        command = command.trim();
        if (matchType == MatchType.REGEX) return command;
        return command.startsWith("/") ? command.substring(1) : command;
    }

    /**
     * @return What is wrong with the command entry, null if it can be used
     */
    public static String commandEntryError(String command, MatchType matchType) {
        if (command.trim().isEmpty()) return "is empty";
        if (matchType == MatchType.REGEX) {
            try {
                CommandUtil.validateCommandRegex(command);
                return null;
            } catch (PatternSyntaxException e) {
                return "is not a valid regular expression (" + e.getDescription() + ")";
            }
        }
        return normalizeCommand(command, matchType).contains(" ") ? "is not a command, it has more than one word" : null;
    }

    /**
     * @return What is wrong with the subcommand entry, null if it can be used
     */
    public static String subcommandEntryError(String subCommand, MatchType matchType) {
        if (matchType == MatchType.REGEX) {
            try {
                CommandUtil.validateSubcommandRegex(subCommand);
                return null;
            } catch (PatternSyntaxException e) {
                return "is not a valid regular expression (" + e.getDescription() + ")";
            } catch (IllegalArgumentException e) {
                return "is not a subcommand (" + e.getMessage() + ")";
            }
        }
        return CommandUtil.tokenize(subCommand).size() < 2 ? "is not a subcommand" : null;
    }

    /**
     * Changes a group, saves the file and publishes the result to all the listeners at once.
     *
     * @return False if the group does not exist or the configuration is not loaded
     */
    public synchronized boolean editGroup(String id, Function<CWGroup, CWGroup> change) {
        CWGroup group = settings.groups.get(id);
        if (group == null || config == null) return false;
        CWGroup changed = change.apply(group);
        Settings.Builder values = read(config);
        config.set("groups." + id, changed.serialize());
        Map<String, CWGroup> groups = new LinkedHashMap<>(settings.groups);
        groups.put(id, changed);
        values.groups = groups;
        settings = new Settings(values);
        return saveConfig();
    }
}
