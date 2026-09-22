package com.spendlocker.ui;

import javafx.scene.text.Font;

/**
 * Loads the reference design's actual typefaces (Archivo for everything, Newsreader for the
 * brand wordmark) from bundled TTFs so the app renders in the same fonts as wealth-book.html
 * instead of the platform default. Must run once before any Scene is styled.
 */
public final class FontLoader {

    private FontLoader() {
    }

    public static void loadAppFonts() {
        loadFont("/com/spendlocker/fonts/Archivo-Regular.ttf");
        loadFont("/com/spendlocker/fonts/Archivo-Medium.ttf");
        loadFont("/com/spendlocker/fonts/Archivo-SemiBold.ttf");
        loadFont("/com/spendlocker/fonts/Archivo-Bold.ttf");
        loadFont("/com/spendlocker/fonts/Newsreader-Regular.ttf");
    }

    private static void loadFont(String resourcePath) {
        Font.loadFont(FontLoader.class.getResourceAsStream(resourcePath), 12);
    }
}
