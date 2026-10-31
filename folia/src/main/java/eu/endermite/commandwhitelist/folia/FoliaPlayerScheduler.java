package eu.endermite.commandwhitelist.folia;

import eu.endermite.commandwhitelist.bukkit.PlayerScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia keeps every player on the thread of its region.
 */
public final class FoliaPlayerScheduler implements PlayerScheduler {

    private final Plugin plugin;

    public FoliaPlayerScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void async(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    @Override
    public void onPlayerThread(Player player, Runnable task) {
        player.getScheduler().run(plugin, scheduled -> task.run(), null);
    }
}
