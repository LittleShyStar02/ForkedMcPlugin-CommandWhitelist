package eu.endermite.commandwhitelist.bukkit.listeners;

import eu.endermite.commandwhitelist.bukkit.CommandWhitelistBukkit;
import eu.endermite.commandwhitelist.common.CWPermission;
import eu.endermite.commandwhitelist.common.CommandFilter;
import eu.endermite.commandwhitelist.common.Settings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class ExecuteListener implements Listener {

    private final CommandWhitelistBukkit plugin;

    public ExecuteListener(CommandWhitelistBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        check(event);
    }

    /**
     * Another plugin could have rewritten the command after the first check.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandRewritten(PlayerCommandPreprocessEvent event) {
        check(event);
    }

    private void check(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission(CWPermission.BYPASS.permission())) return;

        Settings settings = plugin.getConfigCache().get();
        CommandFilter.Verdict verdict = CommandFilter.evaluate(settings, player::hasPermission, event.getMessage(), plugin.aliases);

        plugin.getConfigCache().debug(player.getName() + " ran \"" + event.getMessage() + "\": " + verdict);

        if (verdict == CommandFilter.Verdict.ALLOWED) return;

        event.setCancelled(true);
        CommandFilter.notifyDenied(settings, plugin.messenger(player), player::hasPermission, verdict, event.getMessage());
    }
}
