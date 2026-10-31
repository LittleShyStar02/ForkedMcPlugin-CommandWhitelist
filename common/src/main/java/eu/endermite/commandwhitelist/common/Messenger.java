package eu.endermite.commandwhitelist.common;

import java.util.Map;

/**
 * Output towards one player or console. Implemented by every platform, so that this module does not
 * depend on the Adventure library of any platform.
 */
public interface Messenger {

    /**
     * @param miniMessage  Message in the MiniMessage format
     * @param placeholders Values of the tags like &lt;command&gt;, they are inserted as plain text
     */
    void chat(String miniMessage, Map<String, String> placeholders);

    void actionBar(String miniMessage, Map<String, String> placeholders);

    void sound(String key, float volume, float pitch);
}
