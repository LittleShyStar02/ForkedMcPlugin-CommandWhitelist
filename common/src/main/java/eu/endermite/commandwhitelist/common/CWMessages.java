package eu.endermite.commandwhitelist.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends the messages of the plugin the same way on every platform.
 */
public final class CWMessages {

    private CWMessages() {
    }

    /**
     * Configuration files of older versions use %s for the command and for the group.
     */
    public static String legacyPlaceholders(String message) {
        return message.replaceFirst("%s", "<command>").replaceFirst("%s", "<group>");
    }

    public static Map<String, String> placeholders(String command, String group) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("command", command);
        placeholders.put("group", group);
        return placeholders;
    }

    public static void send(Settings settings, Messenger messenger, String message) {
        messenger.chat(settings.prefix + message, Collections.<String, String>emptyMap());
    }

    static void denied(Settings settings, Messenger messenger, String message, String commandLine) {
        Map<String, String> placeholders = Collections.singletonMap("command", commandLine);
        if (settings.messageType != MessageType.ACTIONBAR) messenger.chat(message, placeholders);
        if (settings.messageType != MessageType.CHAT) messenger.actionBar(message, placeholders);
        if (settings.soundEnabled) messenger.sound(settings.soundKey, settings.soundVolume, settings.soundPitch);
    }
}
