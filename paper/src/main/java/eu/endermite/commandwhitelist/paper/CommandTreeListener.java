package eu.endermite.commandwhitelist.paper;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import eu.endermite.commandwhitelist.bukkit.CommandWhitelistBukkit;
import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandFilter;
import eu.endermite.commandwhitelist.common.Settings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The client suggests literal branches of a command by itself, without asking the server.
 */
public final class CommandTreeListener implements Listener {

    private final CommandWhitelistBukkit plugin;

    // public, because the server calls the handler from another package and a hidden class makes that call fail
    public CommandTreeListener(CommandWhitelistBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onSendCommands(AsyncPlayerSendCommandsEvent event) {
        // the event runs a second time on the main thread only if it did not run asynchronously
        if (!event.isAsynchronous() && event.hasFiredAsync()) return;

        Player player = event.getPlayer();

        if (player.hasPermission(CWPermission.BYPASS.permission())) return;

        Settings settings = plugin.getConfigCache().get();
        Settings.Rules rules = settings.rulesFor(player::hasPermission);

        if (rules.subCommands.isEmpty()) return;

        for (Object root : event.getCommandNode().getChildren())
            prune((CommandNode) root, ((CommandNode) root).getName(), settings, rules);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void prune(CommandNode node, String path, Settings settings, Settings.Rules rules) {
        java.util.Collection<CommandNode> children = node.getChildren();
        children.removeIf(child -> child instanceof LiteralCommandNode
                && !CommandFilter.isBranchVisible(settings, rules, path + " " + child.getName(), plugin.aliases));

        for (CommandNode child : children) {
            if (child instanceof LiteralCommandNode)
                prune(child, path + " " + child.getName(), settings, rules);
        }
    }
}
