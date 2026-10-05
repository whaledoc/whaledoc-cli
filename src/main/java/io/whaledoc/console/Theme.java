package io.whaledoc.console;

/**
 * WhaleDoc's brand color for terminal output, taken from the web app's primary color.
 *
 * <p>The brand color is a light amber, so it is only used for short highlights, never for running text,
 * to stay readable on light terminal backgrounds. Status colors (red, green, yellow, cyan) use the
 * terminal's own palette so they fit the user's light or dark theme.
 */
public final class Theme {

    /** #FBAA53, used where the terminal supports 24-bit color. */
    public static final String BRAND_RGB = "251;170;83";

    /** The closest color in the 256-color palette (#ffaf5f), used everywhere else. */
    public static final int BRAND_256 = 215;

    private Theme() {
    }
}
