package eu.endermite.commandwhitelist.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Where the plugin may run its tasks. Folia does not have one main thread, so it brings its own implementation.
 */
public interface PlayerScheduler {

    void async(Runnable task);

    void onPlayerThread(Player player, Runnable task);

    final class Standard implements PlayerScheduler {

        private final Plugin plugin;

        public Standard(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void async(Runnable task) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }

        @Override
        public void onPlayerThread(Player player, Runnable task) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }
}
