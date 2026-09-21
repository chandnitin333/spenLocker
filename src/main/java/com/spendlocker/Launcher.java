package com.spendlocker;

/**
 * jpackage entry point. A packaged, non-modular JavaFX app must be launched from a main
 * class that does NOT itself extend {@link javafx.application.Application} — the JDK's
 * launcher special-cases Application subclasses and, in a jlink'd runtime image, that path
 * can fail with the misleading "JavaFX runtime components are missing" error instead of
 * the real cause. Delegating through a plain main() avoids that code path entirely.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
