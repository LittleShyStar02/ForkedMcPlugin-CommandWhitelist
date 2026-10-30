package eu.endermite.commandwhitelist.waterfall.listeners;

import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandUtil;
import eu.endermite.commandwhitelist.common.ListMode;
import eu.endermite.commandwhitelist.common.MatchType;
import eu.endermite.commandwhitelist.waterfall.CommandWhitelistWaterfall;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.HashMap;
import java.util.HashSet;

public class WaterfallDefineCommandsListener implements Listener {

    @EventHandler
    public void onProxyDefineCommandsEvent(io.github.waterfallmc.waterfall.event.ProxyDefineCommandsEvent event) {
        if (event.getReceiver() instanceof ProxiedPlayer) {
            ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();
            if (player.hasPermission(CWPermission.BYPASS.permission())) return;
            ListMode mode = CommandWhitelistWaterfall.getConfigCache().commandsMode;
            MatchType matchType = CommandWhitelistWaterfall.getConfigCache().matchType;
            HashSet<String> listedCommands = CommandWhitelistWaterfall.getCommands(player);
            if (mode == ListMode.BLACKLIST) {
                event.getCommands().entrySet().removeIf(commandEntry ->
                        !CommandUtil.isCommandAllowed(mode, matchType, listedCommands, commandEntry.getKey())
                                || !CommandUtil.isCommandAllowed(mode, matchType, listedCommands, commandEntry.getValue().getName()));
                return;
            }
            if (matchType == MatchType.REGEX) {
                event.getCommands().values().removeIf(cmd ->
                        !CommandUtil.isCommandAllowed(mode, matchType, listedCommands, cmd.getName()));
                return;
            }
            HashMap<String, Command> commandHashMap = new HashMap<>();
            listedCommands.forEach(cmdName ->
                    CommandWhitelistWaterfall.getPlugin().getProxy().getPluginManager().getCommands()
                            .stream()
                            .filter(commandEntry -> cmdName.equalsIgnoreCase(commandEntry.getValue().getName()))
                            .forEach(commandEntry -> commandHashMap.put(commandEntry.getKey(), commandEntry.getValue())));
            event.getCommands().values().removeIf((cmd) -> !commandHashMap.containsValue(cmd));
        }
    }
}
