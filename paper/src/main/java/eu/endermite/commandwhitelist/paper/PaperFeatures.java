package eu.endermite.commandwhitelist.paper;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import eu.endermite.commandwhitelist.bukkit.CommandWhitelistBukkit;
import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandFilter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * What Paper adds to the regular listeners: completions are filtered away from the main thread,
 * and on versions that allow it the subcommands are removed from the command tree too.
 */
public final class PaperFeatures implements Listener {

    private final CommandWhitelistBukkit plugin;

    private PaperFeatures(CommandWhitelistBukkit plugin) {
        this.plugin = plugin;
    }

    public static void enable(CommandWhitelistBukkit plugin) {
        plugin.getServer().getPluginManager().registerEvents(new PaperFeatures(plugin), plugin);
        plugin.setExtraAliases(new RedirectAliases(plugin.getServer()));

        try {
            Class.forName("com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent");
            plugin.getServer().getPluginManager().registerEvents(new CommandTreeListener(plugin), plugin);
            plugin.getConfigCache().debug("Command tree filtering is active");
        } catch (ClassNotFoundException | LinkageError e) {
            plugin.getConfigCache().debug("Command tree cannot be filtered on this server: " + e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandTabComplete(AsyncTabCompleteEvent event) {
        if (!(event.getSender() instanceof Player)) return;

        Player player = (Player) event.getSender();

        if (player.hasPermission(CWPermission.BYPASS.permission())) return;

        List<String> filtered = CommandFilter.filterTabCompletion(plugin.getConfigCache().get(), player::hasPermission, event.getBuffer(), event.getCompletions(), plugin.aliases);

        if (filtered == null) {
            plugin.getConfigCache().debug("Actively prevented " + player.getName() + "'s tab completion (sus packet)");
            event.setCancelled(true);
            return;
        }

        plugin.getConfigCache().debug("Async tab completion of " + player.getName() + " for \"" + event.getBuffer() + "\": "
                + filtered.size() + " of " + event.getCompletions().size() + " suggestions kept");

        if (!event.getCompletions().isEmpty()) event.setCompletions(filtered);
    }
}
