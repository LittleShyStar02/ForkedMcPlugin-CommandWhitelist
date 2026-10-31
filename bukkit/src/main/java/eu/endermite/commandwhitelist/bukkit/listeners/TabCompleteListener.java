package eu.endermite.commandwhitelist.bukkit.listeners;

import eu.endermite.commandwhitelist.bukkit.CommandWhitelistBukkit;
import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandFilter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.List;

public class TabCompleteListener implements Listener {

    private final CommandWhitelistBukkit plugin;

    public TabCompleteListener(CommandWhitelistBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player)) return;

        Player player = (Player) event.getSender();

        if (player.hasPermission(CWPermission.BYPASS.permission())) return;

        List<String> filtered = CommandFilter.filterTabCompletion(plugin.getConfigCache().get(), player::hasPermission, event.getBuffer(), event.getCompletions(), plugin.aliases);

        if (filtered == null) {
            plugin.getConfigCache().debug("Actively prevented " + player.getName() + "'s tab completion (sus packet)");
            event.setCancelled(true);
            return;
        }

        event.setCompletions(filtered);
    }
}
