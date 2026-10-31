package eu.endermite.commandwhitelist.bukkit;

import eu.endermite.commandwhitelist.common.Messenger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bukkit and Waterfall send their messages through the Adventure library bundled in the jar.
 */
public final class BukkitMessenger implements Messenger {

    private final Audience audience;

    public BukkitMessenger(Audience audience) {
        this.audience = audience;
    }

    public static Component parse(String miniMessage, Map<String, String> placeholders) {
        List<TagResolver> resolvers = new ArrayList<>();
        for (Map.Entry<String, String> placeholder : placeholders.entrySet())
            resolvers.add(Placeholder.unparsed(placeholder.getKey(), placeholder.getValue()));
        return MiniMessage.miniMessage().deserialize(miniMessage, TagResolver.resolver(resolvers));
    }

    @Override
    public void chat(String miniMessage, Map<String, String> placeholders) {
        audience.sendMessage(parse(miniMessage, placeholders));
    }

    @Override
    public void actionBar(String miniMessage, Map<String, String> placeholders) {
        audience.sendActionBar(parse(miniMessage, placeholders));
    }

    @Override
    public void sound(String key, float volume, float pitch) {
        audience.playSound(Sound.sound(Key.key(key), Sound.Source.UI, volume, pitch));
    }
}
