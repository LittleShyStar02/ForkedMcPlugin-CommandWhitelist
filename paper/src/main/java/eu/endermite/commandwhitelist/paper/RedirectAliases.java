package eu.endermite.commandwhitelist.paper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import org.bukkit.Server;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Modern servers keep aliases like tp -> teleport as redirects in the command tree, where Bukkit cannot see them.
 * Server internals have no stable API: when they cannot be read the table stays empty and only Bukkit aliases count.
 */
final class RedirectAliases implements Function<String, Collection<String>> {

    private final Server server;
    private volatile Map<String, Set<String>> table;

    RedirectAliases(Server server) {
        this.server = server;
    }

    @Override
    public Collection<String> apply(String label) {
        Map<String, Set<String>> current = table;
        if (current == null) {
            current = read();
            table = current;
        }
        Set<String> names = current.get(label.toLowerCase(Locale.ROOT));
        return names == null ? Collections.<String>emptyList() : names;
    }

    private Map<String, Set<String>> read() {
        try {
            Object minecraftServer = server.getClass().getMethod("getServer").invoke(server);
            Object commands = commandsOf(minecraftServer);
            for (Method method : commands.getClass().getMethods()) {
                if (method.getParameterCount() == 0 && CommandDispatcher.class.isAssignableFrom(method.getReturnType())) {
                    CommandDispatcher<?> dispatcher = (CommandDispatcher<?>) method.invoke(commands);
                    return group(dispatcher.getRoot().getChildren());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // only the aliases Bukkit knows about will be recognized
        }
        return Collections.emptyMap();
    }

    /**
     * Newer servers have Mojang's method names, older ones only have the field added by Bukkit.
     */
    private static Object commandsOf(Object minecraftServer) throws ReflectiveOperationException {
        try {
            return minecraftServer.getClass().getMethod("getCommands").invoke(minecraftServer);
        } catch (NoSuchMethodException e) {
            return minecraftServer.getClass().getField("vanillaCommandDispatcher").get(minecraftServer);
        }
    }

    private static Map<String, Set<String>> group(Collection<? extends CommandNode<?>> roots) {
        Map<CommandNode<?>, List<String>> byTarget = new IdentityHashMap<>();
        for (CommandNode<?> node : roots) {
            CommandNode<?> target = node.getRedirect() != null ? node.getRedirect() : node;
            byTarget.computeIfAbsent(target, key -> new ArrayList<>()).add(node.getName());
        }
        Map<String, Set<String>> table = new HashMap<>();
        for (List<String> names : byTarget.values()) {
            if (names.size() < 2) continue;
            for (String name : names) {
                Set<String> others = new LinkedHashSet<>(names);
                others.remove(name);
                table.put(name.toLowerCase(Locale.ROOT), others);
            }
        }
        return table;
    }
}
