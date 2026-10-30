package eu.endermite.commandwhitelist.bukkit.listeners;

import eu.endermite.commandwhitelist.bukkit.CommandWhitelistBukkit;
import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandUtil;
import eu.endermite.commandwhitelist.common.ListMode;
import eu.endermite.commandwhitelist.common.MatchType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;

public class PlayerCommandSendListener implements Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public void PlayerCommandSendEvent(org.bukkit.event.player.PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(CWPermission.BYPASS.permission())) return;
        HashSet<String> commandList = CommandWhitelistBukkit.getCommands(player);
        ListMode mode = CommandWhitelistBukkit.getConfigCache().commandsMode;
        MatchType matchType = CommandWhitelistBukkit.getConfigCache().matchType;
        event.getCommands().removeIf((cmd) -> !CommandUtil.isCommandAllowed(mode, matchType, commandList, cmd));
    }
}
