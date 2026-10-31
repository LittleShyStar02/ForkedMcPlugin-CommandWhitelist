package eu.endermite.commandwhitelist.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends the messages of the plugin the same way on every platform.
 */
public final class CWMessages {

    private CWMessages() {
    }

    private static final Pattern LEGACY_CODE = Pattern.compile("[&§](?:#([0-9a-fA-F]{6})|([0-9a-fA-FklmnorKLMNOR]))");
    private static final String[] LEGACY_TAGS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    /**
     * Lets the legacy codes (&amp;c, &amp;#ff8800, section sign) be used in the messages along with MiniMessage.
     *
     * @return The message with the legacy codes replaced by MiniMessage tags
     */
    public static String toMiniMessage(String message) {
        if (message.indexOf('&') < 0 && message.indexOf('§') < 0) return message;

        Matcher matcher = LEGACY_CODE.matcher(message);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String tag;

            if (matcher.group(1) != null) {
                tag = "<#" + matcher.group(1) + ">";
            } else {
                char code = Character.toLowerCase(matcher.group(2).charAt(0));
                int index = Character.digit(code, 16);

                if (index >= 0) tag = "<" + LEGACY_TAGS[index] + ">";
                else if (code == 'k') tag = "<obfuscated>";
                else if (code == 'l') tag = "<bold>";
                else if (code == 'm') tag = "<strikethrough>";
                else if (code == 'n') tag = "<underlined>";
                else if (code == 'o') tag = "<italic>";
                else tag = "<reset>";
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(tag));
        }

        matcher.appendTail(result);

        return result.toString();
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
