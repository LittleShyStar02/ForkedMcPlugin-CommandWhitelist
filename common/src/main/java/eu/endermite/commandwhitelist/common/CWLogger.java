package eu.endermite.commandwhitelist.common;

/**
 * Logger of the platform the plugin is running on.
 */
public interface CWLogger {

    void info(String message);

    void warn(String message);
}
