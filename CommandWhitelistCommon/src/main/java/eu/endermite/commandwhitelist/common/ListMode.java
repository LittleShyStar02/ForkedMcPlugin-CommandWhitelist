package eu.endermite.commandwhitelist.common;

/**
 * Defines how a list of entries from the groups should be interpreted.
 * <ul>
 *     <li>WHITELIST - only the listed entries are allowed</li>
 *     <li>BLACKLIST - the listed entries are denied, everything else is allowed</li>
 * </ul>
 */
public enum ListMode {

    WHITELIST, BLACKLIST

}
