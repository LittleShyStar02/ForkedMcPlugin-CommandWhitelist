package eu.endermite.commandwhitelist.common;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;
import net.kyori.adventure.key.Key;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.PatternSyntaxException;

public class ConfigCache {

    private final File configFile;
    private ConfigFile config;
    private final Object logger;
    private final boolean canDoProtocolLib, supportsCommandDeniedMessage;
    private final HashMap<String, CWGroup> groupList = new LinkedHashMap<>();
    public String prefix, command_denied, no_permission, no_such_subcommand, config_reloaded, added_to_whitelist,
            removed_from_whitelist, group_doesnt_exist, subcommand_denied, command_denied_sound_key;
    public boolean useProtocolLib = false;
    public boolean command_denied_sound_enabled = false;
    public float command_denied_sound_volume = 1.0f;
    public float command_denied_sound_pitch = 1.0f;
    public MessageType messageType = MessageType.CHAT;
    public ListMode commandsMode = ListMode.WHITELIST;
    public ListMode subcommandsMode = ListMode.BLACKLIST;
    public MatchType matchType = MatchType.LITERAL;
    public boolean debug = false;
    private final String default_command_denied_sound_key = "entity.villager.no";

    public ConfigCache(File configFile, boolean canDoProtocolLib, boolean supportsCommandDeniedMessage, Object logger) {
        this.configFile = configFile;
        this.canDoProtocolLib = canDoProtocolLib;
        this.supportsCommandDeniedMessage = supportsCommandDeniedMessage;
        this.logger = logger;

        try {
            reloadConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean reloadConfig() {

        createFiles();
        try {
            config = ConfigFile.loadConfig(configFile);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        config.addDefault("messages.prefix", "CommandWhitelist > ");
        if (supportsCommandDeniedMessage) {
            config.addDefault("messages.command_denied", "No such command.");
            config.addDefault("messages.subcommand_denied", "You cannot use this subcommand");
        }

        config.addDefault("messages.no_permission", "<red>You don't have permission to do this.");
        config.addDefault("messages.no_such_subcommand", "<red>No subcommand by that name.");
        config.addDefault("messages.config_reloaded", "<yellow>Configuration reloaded.");
        config.addDefault("messages.added_to_whitelist", "<yellow>Whitelisted command <gold>%s <yellow>for permission <gold>%s");
        config.addDefault("messages.removed_from_whitelist", "<yellow>Removed command <gold>%s <yellow>from permission <gold>%s");
        config.addDefault("messages.group_doesnt_exist", "<red>Group doesn't exist or error occured");

        config.addComment("messages", "Messages use MiniMessage formatting (https://docs.adventure.kyori.net/minimessage/format)");

        config.addDefault("mode.commands", ListMode.WHITELIST.toString(), "How the \"commands\" lists of the groups are interpreted.\nWHITELIST - players can only see/use the listed commands\nBLACKLIST - players can see/use every command except the listed ones");
        config.addDefault("mode.subcommands", ListMode.BLACKLIST.toString(), "How the \"subcommands\" lists of the groups are interpreted.\nBLACKLIST - players cannot see/use the listed subcommands\nWHITELIST - a command with listed subcommands can only be used with one of them\n(commands that have no listed subcommands are not restricted)");
        config.addDefault("mode.matchtype", MatchType.LITERAL.toString(), "How every entry of the \"commands\" and \"subcommands\" lists is compared with what the player typed.\nLITERAL (or NORMAL) - the entry is the exact command, for example \"tpa\" or \"help about\"\nREGEX - the entry is a case-insensitive regular expression, for example 'ciao\\w' matches ciao1, ciaoX...\nIn REGEX a command entry must match the whole command, a subcommand entry must match the beginning of the command line\n(words separated by one space, no slash), for example 'gamemode (creative|spectator)'.\nWith the WHITELIST mode for subcommands, the part of the entry before its first space says to which commands it applies.\nWrite regular expressions between single quotes in the groups, so that backslashes are kept as they are.");

        if (canDoProtocolLib)
            config.addDefault("use_protocollib", false, "Do not enable if you don't have issues with aliased commands.\nThis requires server restart to take effect.");

        if (supportsCommandDeniedMessage) {
            config.addDefault("message_type", MessageType.CHAT.toString(), "Valid message types are CHAT and ACTIONBAR and CHAT_AND_ACTIONBAR.");
            config.addDefault("command_denied_sound.enabled", false);
            config.addDefault("command_denied_sound.key", "entity.villager.no");
            config.addDefault("command_denied_sound.volume", 1.0f);
            config.addDefault("command_denied_sound.pitch", 1.0f);
        }

        if (config.isNew()) {
            List<String> exampleCommands = new ArrayList<>();
            exampleCommands.add("example");
            List<String> exampleSubCommands = new ArrayList<>();
            exampleSubCommands.add("example of");
            String exampleCustomCommandDeniedMessage = "You don't have commandwhitelist.group.example permission.";

            config.addExample("groups.example.commands", exampleCommands, "These are the commands of the group \"example\". Depending on mode.commands, players will only be able to see/use them (WHITELIST) or will NOT be able to see/use them (BLACKLIST)");
            config.addExample("groups.example.subcommands", exampleSubCommands, "These are the subcommands of the group \"example\". Depending on mode.subcommands, players will NOT be able to see/use them (BLACKLIST) or will only be able to use these subcommands of their command (WHITELIST)");
            config.addExample("groups.example.custom_command_denied_message", exampleCustomCommandDeniedMessage, "This is a custom message that players will see if they do not have commandwhitelist.group.<group_name> permission.\ncommandwhitelist.group.example in this case\nIf you don't want to use a custom message, set custom_command_denid_message: \"\"");
            config.addComment("groups.example", "All groups except from default require commandwhitelist.group.<group_name> permission\ncommandwhitelist.group.example in this case\n If you wish to leave the list empty, put \"commands: []\" or \"subcommands: []\"");
        }

        config.makeSectionLenient("groups");
        List<String> defaultCommands = new ArrayList<>();
        defaultCommands.add("help");
        defaultCommands.add("spawn");
        defaultCommands.add("bal");
        defaultCommands.add("balance");
        defaultCommands.add("baltop");
        defaultCommands.add("pay");
        defaultCommands.add("r");
        defaultCommands.add("msg");
        defaultCommands.add("tpa");
        defaultCommands.add("tpahere");
        defaultCommands.add("tpaccept");
        defaultCommands.add("tpdeny");
        defaultCommands.add("warp");
        List<String> defaultSubcommands = new ArrayList<>();
        defaultSubcommands.add("help about");

        String defaultCustomCommandDeniedMessage = "";

        config.addDefault("groups.default", new CWGroup("default", defaultCommands, defaultSubcommands, defaultCustomCommandDeniedMessage).serialize());

        prefix = config.getString("messages.prefix");
        command_denied = config.getString("messages.command_denied", "No such command.");
        subcommand_denied = config.getString("messages.subcommand_denied", "You cannot use this subcommand");
        no_permission = config.getString("messages.no_permission");
        no_such_subcommand = config.getString("messages.no_such_subcommand");
        config_reloaded = config.getString("messages.config_reloaded");
        added_to_whitelist = config.getString("messages.added_to_whitelist");
        removed_from_whitelist = config.getString("messages.removed_from_whitelist");
        group_doesnt_exist = config.getString("messages.group_doesnt_exist");
        useProtocolLib = config.getBoolean("use_protocollib");
        command_denied_sound_enabled =  config.getBoolean("command_denied_sound.enabled", false);
        if(command_denied_sound_enabled) {
            try {
                String temp_command_denied_sound_key = config.getString("command_denied_sound.key");
                if(temp_command_denied_sound_key == null) {
                    warn("Invalid sound key. Using " + default_command_denied_sound_key);
                    command_denied_sound_key = default_command_denied_sound_key;
                } else {
                    try {
                    Key isSound = Key.key(temp_command_denied_sound_key);
                    command_denied_sound_key = isSound.asString();
                    } catch (Exception e) {
                        warn("Invalid sound key. Using " + default_command_denied_sound_key);
                        command_denied_sound_key = default_command_denied_sound_key;
                    }
                    command_denied_sound_key = command_denied_sound_key.toLowerCase(Locale.ENGLISH);
                }
                command_denied_sound_volume = config.getFloat("command_denied_sound.volume");
                command_denied_sound_pitch = config.getFloat("command_denied_sound.pitch");
            } catch (Exception e) {
                warn("Invalid sound. Using default sound configurations.");
                command_denied_sound_key = default_command_denied_sound_key;
                command_denied_sound_volume = 1.0f;
                command_denied_sound_pitch = 1.0f;
            }
        }
        debug = config.getBoolean("debug", false);
        commandsMode = loadListMode("mode.commands", ListMode.WHITELIST);
        subcommandsMode = loadListMode("mode.subcommands", ListMode.BLACKLIST);
        matchType = loadMatchType();
        try {
            String chatTypeId = config.getString("message_type");
            if (chatTypeId == null) {
                warn("Invalid message type. Using CHAT.");
            } else {
                messageType = MessageType.valueOf(chatTypeId.toUpperCase(Locale.ENGLISH));
            }
        } catch (IllegalArgumentException e) {
            warn("Invalid message type. Using CHAT.");
        }

        ConfigSection groupSection = config.getConfigSection("groups");
        for (String key : groupSection.getKeys(false)) {
            groupList.put(key, loadCWGroup(key, groupSection));
        }

        if (useProtocolLib) {
            warn("Protocollib support is deprecated and will be removed in future versions. If you have an actual" +
                    " use case for it (aka plugin does not work without it), please reach out via discord (https://discord.youhavetrouble.me).");
        }

        return saveConfig();
    }

    private ListMode loadListMode(String path, ListMode fallback) {
        String value = config.getString(path);
        if (value == null) {
            warn("Invalid " + path + ". Using " + fallback + ".");
            return fallback;
        }
        try {
            return ListMode.valueOf(value.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            warn("Invalid " + path + " \"" + value + "\", valid modes are WHITELIST and BLACKLIST. Using " + fallback + ".");
            return fallback;
        }
    }

    private MatchType loadMatchType() {
        String value = config.getString("mode.matchtype");
        if (value == null) {
            warn("Invalid mode.matchtype. Using " + MatchType.LITERAL + ".");
            return MatchType.LITERAL;
        }
        value = value.trim().toUpperCase(Locale.ENGLISH);
        if (value.equals("NORMAL")) return MatchType.LITERAL;
        try {
            return MatchType.valueOf(value);
        } catch (IllegalArgumentException e) {
            warn("Invalid mode.matchtype \"" + value + "\", valid match types are LITERAL (or NORMAL) and REGEX. Using " + MatchType.LITERAL + ".");
            return MatchType.LITERAL;
        }
    }

    private boolean saveConfig() {
        try {
            config.save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createFiles() {
        try {
            File parent = new File(configFile.getParent());
            if (!parent.exists())
                parent.mkdir();
            if (!configFile.exists())
                configFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public CWGroup loadCWGroup(String id, ConfigSection section) {
        HashSet<String> commands = new HashSet<>();
        for (String cmd : section.getStringList(id + ".commands")) {
            if (matchType == MatchType.REGEX) {
                try {
                    CommandUtil.validateCommandRegex(cmd);
                } catch (PatternSyntaxException e) {
                    warn("CommandWhitelist - \"" + cmd + "\" is not a valid regular expression (" + e.getDescription() + "). Skipping it.");
                    continue;
                }
                commands.add(cmd);
                continue;
            }
            if (cmd.contains(" ")) {
                String[] cmdSplit = cmd.split(" ");
                warn("CommandWhitelist - \"" + cmd + "\" is not a command. Loading it as \"" + cmdSplit[0] + "\".");
                cmd = cmdSplit[0];
            }
            if (commands.contains(cmd)) continue;
            commands.add(cmd);
        }
        List<String> subCommands = new ArrayList<>();
        for (String subCmd : section.getStringList(id + ".subcommands")) {
            if (matchType == MatchType.REGEX) {
                try {
                    CommandUtil.validateSubcommandRegex(subCmd);
                } catch (PatternSyntaxException e) {
                    warn("CommandWhitelist - \"" + subCmd + "\" is not a valid regular expression (" + e.getDescription() + "). Skipping it.");
                    continue;
                } catch (IllegalArgumentException e) {
                    warn("CommandWhitelist - \"" + subCmd + "\" is not a subcommand (" + e.getMessage() + "). Skipping it.");
                    continue;
                }
                subCommands.add(subCmd);
                continue;
            }
            if (!subCmd.contains(" ")) {
                warn("CommandWhitelist - \"" + subCmd + "\" is not a subcommand. Skipping it.");
                continue;
            }
            subCommands.add(subCmd);
        }
        String customCommandDeniedMessage = section.getString(id + ".custom_command_denied_message");
        return new CWGroup(id, commands, subCommands, customCommandDeniedMessage);
    }

    public void saveCWGroup(String id, CWGroup group) {
        config.set("groups." + id, group.serialize());
        saveConfig();
    }

    public HashMap<String, CWGroup> getGroupList() {
        return groupList;
    }

    public void warn(String log) {
        if (logger == null) {
            System.out.println("WARNING: "+log);
            return;
        }
        if (logger instanceof org.slf4j.Logger) {
            ((org.slf4j.Logger) logger).warn(log);
            return;
        }
        if (logger instanceof java.util.logging.Logger) {
            ((java.util.logging.Logger) logger).warning(log);
            return;
        }
    }

    public void debug(String log) {
        if (!debug) return;
        if (logger == null) {
            System.out.println("DEBUG: "+log);
            return;
        }
        if (logger instanceof org.slf4j.Logger) {
            ((org.slf4j.Logger) logger).info(log);
            return;
        }
        if (logger instanceof java.util.logging.Logger) {
            ((java.util.logging.Logger) logger).info(log);
            return;
        }
    }

}
