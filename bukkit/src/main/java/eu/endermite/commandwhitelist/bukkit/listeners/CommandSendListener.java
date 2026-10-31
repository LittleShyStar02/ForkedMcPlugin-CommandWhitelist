package eu.endermite.commandwhitelist.bukkit.listeners;

import eu.endermite.commandwhitelist.bukkit.CommandWhitelistBukkit;
import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandFilter;
import eu.endermite.commandwhitelist.common.Settings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

public class CommandSendListener implements Listener {

    private final CommandWhitelistBukkit plugin;

    public CommandSendListener(CommandWhitelistBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(CWPermission.BYPASS.permission())) return;
        Settings settings = plugin.getConfigCache().get();
        event.getCommands().removeIf(name -> !CommandFilter.isVisible(settings, player::hasPermission, name, plugin.aliases));
    }
}
