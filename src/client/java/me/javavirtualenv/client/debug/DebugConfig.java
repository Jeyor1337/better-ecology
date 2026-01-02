package me.javavirtualenv.client.debug;

/**
 * Static configuration class for the ecology debug overlay system.
 * Controls whether debug information is rendered above mobs.
 */
public final class DebugConfig {

    private static boolean debugEnabled = false;

    private DebugConfig() {
        // Static utility class
    }

    /**
     * Returns whether debug mode is currently enabled.
     *
     * @return true if debug rendering is active
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Toggles debug mode on or off.
     *
     * @return the new debug state after toggling
     */
    public static boolean toggleDebug() {
        debugEnabled = !debugEnabled;
        return debugEnabled;
    }

    /**
     * Sets the debug mode to a specific state.
     *
     * @param enabled true to enable debug mode, false to disable
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }
}
