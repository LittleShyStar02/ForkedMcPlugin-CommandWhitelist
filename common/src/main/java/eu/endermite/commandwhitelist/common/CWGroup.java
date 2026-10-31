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
    private final Set<String> hidden;

    public CWGroup(String id, Collection<String> commands, Collection<String> subCommands, String commandDeniedMessage) {
        this(id, commands, subCommands, Collections.<String>emptyList(), commandDeniedMessage);
    }

    public CWGroup(String id, Collection<String> commands, Collection<String> subCommands, Collection<String> hidden, String commandDeniedMessage) {
        this.id = id;
        this.permission = "commandwhitelist.group." + id;
        this.commands = Collections.unmodifiableSet(new LinkedHashSet<>(commands));
        this.subCommands = Collections.unmodifiableSet(new LinkedHashSet<>(subCommands));
        this.hidden = Collections.unmodifiableSet(new LinkedHashSet<>(hidden));
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
     * @return Commands and subcommands that can be used but are never suggested
     */
    public Set<String> getHidden() {
        return hidden;
    }

    /**
     * @return Custom message, null or empty if the group has none
     */
    public String getCommandDeniedMessage() {
        return commandDeniedMessage;
    }

    public CWGroup withCommands(Collection<String> newCommands) {
        return new CWGroup(id, newCommands, subCommands, hidden, commandDeniedMessage);
    }

    public CWGroup withSubCommands(Collection<String> newSubCommands) {
        return new CWGroup(id, commands, newSubCommands, hidden, commandDeniedMessage);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> serializedGroup = new LinkedHashMap<>();
        serializedGroup.put("commands", new ArrayList<>(commands));
        serializedGroup.put("subcommands", new ArrayList<>(subCommands));

        // optional, written only when the group uses it
        if (!hidden.isEmpty())
            serializedGroup.put("hidden", new ArrayList<>(hidden));

        if (commandDeniedMessage != null)
            serializedGroup.put("custom_command_denied_message", commandDeniedMessage);

        return serializedGroup;
    }
}
