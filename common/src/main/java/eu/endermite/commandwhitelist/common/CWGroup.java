package eu.endermite.commandwhitelist.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable group of commands. Changing a group creates a new instance, so a group can be read from any thread.
 */
public final class CWGroup {

    private final String id, permission, commandDeniedMessage;
    private final Set<String> commands;
    private final Set<String> subCommands;

    public CWGroup(String id, Collection<String> commands, Collection<String> subCommands, String commandDeniedMessage) {
        this.id = id;
        this.permission = "commandwhitelist.group." + id;
        this.commands = Collections.unmodifiableSet(new LinkedHashSet<>(commands));
        this.subCommands = Collections.unmodifiableSet(new LinkedHashSet<>(subCommands));
        this.commandDeniedMessage = commandDeniedMessage;
    }

    public String getId() {
        return id;
    }

    public String getPermission() {
        return permission;
    }

    public Set<String> getCommands() {
        return commands;
    }

    public Set<String> getSubCommands() {
        return subCommands;
    }

    /**
     * @return Custom message, null or empty if the group has none
     */
    public String getCommandDeniedMessage() {
        return commandDeniedMessage;
    }

    public CWGroup withCommands(Collection<String> newCommands) {
        return new CWGroup(id, newCommands, subCommands, commandDeniedMessage);
    }

    public CWGroup withSubCommands(Collection<String> newSubCommands) {
        return new CWGroup(id, commands, newSubCommands, commandDeniedMessage);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> serializedGroup = new LinkedHashMap<>();
        serializedGroup.put("commands", new ArrayList<>(commands));
        serializedGroup.put("subcommands", new ArrayList<>(subCommands));
        if (commandDeniedMessage != null)
            serializedGroup.put("custom_command_denied_message", commandDeniedMessage);
        return serializedGroup;
    }
}
