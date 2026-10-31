package eu.endermite.commandwhitelist.common;

/**
 * Defines how a single entry of the commands/subcommands lists is compared with what the player typed.
 * <ul>
 *     <li>LITERAL - the entry is the exact command (or the exact words of the subcommand)</li>
 *     <li>REGEX - the entry is a regular expression, case sensitive only in the WHITELIST mode</li>
 * </ul>
 */
public enum MatchType {

    LITERAL, REGEX

}
