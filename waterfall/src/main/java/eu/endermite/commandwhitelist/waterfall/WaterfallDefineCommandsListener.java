package eu.endermite.commandwhitelist.waterfall;

import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandFilter;
import eu.endermite.commandwhitelist.common.Settings;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaterfallDefineCommandsListener implements Listener {

    private final CommandWhitelistWaterfall plugin;

    WaterfallDefineCommandsListener(CommandWhitelistWaterfall plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onProxyDefineCommandsEvent(io.github.waterfallmc.waterfall.event.ProxyDefineCommandsEvent event) {
        if (!(event.getReceiver() instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();
        if (player.hasPermission(CWPermission.BYPASS.permission())) return;
        Settings settings = plugin.getConfigCache().get();

        // the names of every command are collected once, so one player joining costs a single pass over the commands
        Map<Command, List<String>> names = new HashMap<>();
        for (Map.Entry<String, Command> entry : event.getCommands().entrySet())
            names.computeIfAbsent(entry.getValue(), command -> new ArrayList<>()).add(entry.getKey());

        event.getCommands().entrySet().removeIf(entry -> {
            Collection<String> sameCommand = names.get(entry.getValue());
            return !CommandFilter.isVisible(settings, player::hasPermission, entry.getKey(), label -> sameCommand);
        });
    }
}
