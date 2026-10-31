package eu.endermite.commandwhitelist.common;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Immutable state of one loaded configuration. A reload builds a new instance and replaces the old one at once,
 * so listeners on any thread only ever see a complete configuration.
 */
public final class Settings {

    public final String prefix, commandDenied, subcommandDenied, noPermission, configReloaded, configReloadFailed,
            addedToWhitelist, removedFromWhitelist, groupDoesntExist, invalidEntry;
    public final boolean soundEnabled;
    public final String soundKey;
    public final float soundVolume, soundPitch;
    public final MessageType messageType;
    public final ListMode commandsMode, subcommandsMode;
    public final MatchType matchType;
    public final boolean debug;
    public final Map<String, CWGroup> groups;

    private final List<CWGroup> groupOrder;
    private final Map<BitSet, Rules> rulesCache = new ConcurrentHashMap<>();

    Settings(Builder b) {
        prefix = b.prefix;
        commandDenied = b.commandDenied;
        subcommandDenied = b.subcommandDenied;
        noPermission = b.noPermission;
        configReloaded = b.configReloaded;
        configReloadFailed = b.configReloadFailed;
        addedToWhitelist = b.addedToWhitelist;
        removedFromWhitelist = b.removedFromWhitelist;
        groupDoesntExist = b.groupDoesntExist;
        invalidEntry = b.invalidEntry;
        soundEnabled = b.soundEnabled;
        soundKey = b.soundKey;
        soundVolume = b.soundVolume;
        soundPitch = b.soundPitch;
        messageType = b.messageType;
        commandsMode = b.commandsMode;
        subcommandsMode = b.subcommandsMode;
        matchType = b.matchType;
        debug = b.debug;
        groups = Collections.unmodifiableMap(new LinkedHashMap<>(b.groups));
        groupOrder = new ArrayList<>(groups.values());
    }

    /**
     * Commands and subcommands of the groups available to a player, merged together.
     */
    public static final class Rules {
        public final Set<String> commands, subCommands;

        Rules(Set<String> commands, Set<String> subCommands) {
            this.commands = Collections.unmodifiableSet(commands);
            this.subCommands = Collections.unmodifiableSet(subCommands);
        }
    }

    /**
     * Permissions are checked on every call, because they can change at any moment.
     * The merged result is shared by all the players having the same set of groups.
     *
     * @param permissions Permission check of the player
     * @return Union of the default group and of all the groups the player has a permission of
     */
    public Rules rulesFor(Predicate<String> permissions) {
        BitSet available = new BitSet(groupOrder.size());
        for (int i = 0; i < groupOrder.size(); i++) {
            CWGroup group = groupOrder.get(i);
            if (isDefault(group) || permissions.test(group.getPermission())) available.set(i);
        }
        return rulesCache.computeIfAbsent(available, this::merge);
    }

    private Rules merge(BitSet available) {
        Set<String> commands = new LinkedHashSet<>();
        Set<String> subCommands = new LinkedHashSet<>();
        for (int i = available.nextSetBit(0); i >= 0; i = available.nextSetBit(i + 1)) {
            commands.addAll(groupOrder.get(i).getCommands());
            subCommands.addAll(groupOrder.get(i).getSubCommands());
        }
        return new Rules(commands, subCommands);
    }

    /**
     * Picks the custom message among the groups that caused the denial: in the WHITELIST mode the groups listing
     * the command that the player is missing, in the BLACKLIST mode the groups of the player listing the command.
     *
     * @param label       Label of the denied command
     * @param permissions Permission check of the player
     * @return Command denied message
     */
    public String commandDeniedMessage(String label, Predicate<String> permissions) {
        for (CWGroup group : groupOrder) {
            String custom = group.getCommandDeniedMessage();
            if (custom == null || custom.isEmpty()) continue;
            boolean available = isDefault(group) || permissions.test(group.getPermission());
            if (available != (commandsMode == ListMode.BLACKLIST)) continue;
            if (!CommandUtil.isCommandListed(commandsMode, matchType, group.getCommands(), label)
                    && !CommandUtil.isCommandListed(commandsMode, matchType, group.getCommands(), CommandUtil.stripNamespace(label)))
                continue;
            return custom;
        }
        return commandDenied;
    }

    private static boolean isDefault(CWGroup group) {
        return group.getId().equalsIgnoreCase("default");
    }

    static final class Builder {
        String prefix = "CommandWhitelist > ";
        String commandDenied = "No such command.";
        String subcommandDenied = "You cannot use this subcommand";
        String noPermission = "<red>You don't have permission to do this.";
        String configReloaded = "<yellow>Configuration reloaded.";
        String configReloadFailed = "<red>Configuration could not be loaded, the previous one stays active. Check the console.";
        String addedToWhitelist = "<yellow>Whitelisted command <gold><command> <yellow>for permission <gold><group>";
        String removedFromWhitelist = "<yellow>Removed command <gold><command> <yellow>from permission <gold><group>";
        String groupDoesntExist = "<red>Group doesn't exist or error occured";
        String invalidEntry = "<red>Not a valid entry: <gold><command>";
        boolean soundEnabled = false;
        String soundKey = "entity.villager.no";
        float soundVolume = 1.0f;
        float soundPitch = 1.0f;
        MessageType messageType = MessageType.CHAT;
        ListMode commandsMode = ListMode.WHITELIST;
        ListMode subcommandsMode = ListMode.BLACKLIST;
        MatchType matchType = MatchType.LITERAL;
        boolean debug = false;
        Map<String, CWGroup> groups = new LinkedHashMap<>();
    }
}
