package com.spendlocker.security;

import com.spendlocker.db.DatabaseManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Bridges to a small native Swift CLI (compiled on demand, see native/TouchIDHelper.swift)
 * that stores the master password in the macOS Keychain behind a Touch ID access control.
 * JavaFX/Java alone cannot talk to Touch ID directly — this is the actual native integration.
 * macOS only; {@link #isSupported()} is false on every other platform.
 */
public class TouchIdService {

    private static final Path HELPER_BINARY = Path.of(DatabaseManager.vaultDirectory(), "touchid-helper");
    private static final Path ENABLED_MARKER = Path.of(DatabaseManager.vaultDirectory(), "touchid.enabled");

    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    public boolean isEnrolled() {
        return Files.exists(ENABLED_MARKER);
    }

    /** True if this Mac has Touch ID hardware enrolled and the helper compiled successfully. */
    public boolean isAvailable() {
        if (!isSupported() || !ensureCompiled()) return false;
        try {
            Process process = new ProcessBuilder(HELPER_BINARY.toString(), "check").start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Stores the master password behind Touch ID and marks this vault as Touch-ID-enrolled. */
    public boolean enable(String masterPassword) {
        if (!isSupported() || !ensureCompiled()) return false;
        try {
            Process process = new ProcessBuilder(HELPER_BINARY.toString(), "store").start();
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write((masterPassword + "\n").getBytes(StandardCharsets.UTF_8));
            }
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                Files.writeString(ENABLED_MARKER, "enabled");
                return true;
            }
            return false;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public void disable() {
        if (isSupported() && ensureCompiled()) {
            try {
                new ProcessBuilder(HELPER_BINARY.toString(), "delete").start().waitFor(10, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException ignored) {
                // best-effort; the marker removal below is what actually matters to the app
            }
        }
        try {
            Files.deleteIfExists(ENABLED_MARKER);
        } catch (IOException ignored) {
        }
    }

    /** Triggers the native Touch ID prompt and returns the stored master password on success. */
    public Optional<String> unlock() {
        if (!isSupported() || !isEnrolled() || !ensureCompiled()) return Optional.empty();
        try {
            Process process = new ProcessBuilder(HELPER_BINARY.toString(), "retrieve").start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }
            boolean finished = process.waitFor(60, TimeUnit.SECONDS); // biometric prompt needs real time
            if (finished && process.exitValue() == 0 && output != null && !output.isBlank()) {
                return Optional.of(output);
            }
            return Optional.empty();
        } catch (IOException | InterruptedException e) {
            return Optional.empty();
        }
    }

    private synchronized boolean ensureCompiled() {
        if (Files.exists(HELPER_BINARY)) return true;
        try {
            Path swiftSource = Files.createTempFile("touchid-helper", ".swift");
            try (InputStream in = getClass().getResourceAsStream("/native/TouchIDHelper.swift")) {
                if (in == null) return false;
                Files.copy(in, swiftSource, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.createDirectories(HELPER_BINARY.getParent());
            Process compile = new ProcessBuilder(
                    "swiftc", swiftSource.toString(), "-o", HELPER_BINARY.toString(),
                    "-framework", "LocalAuthentication", "-framework", "Security")
                    .redirectErrorStream(true)
                    .start();
            boolean compiled = compile.waitFor(120, TimeUnit.SECONDS) && compile.exitValue() == 0;
            Files.deleteIfExists(swiftSource);
            if (!compiled || !Files.exists(HELPER_BINARY)) return false;

            // Ad-hoc code signing is required for this binary's Keychain calls to work reliably.
            Process sign = new ProcessBuilder("codesign", "-s", "-", HELPER_BINARY.toString())
                    .redirectErrorStream(true)
                    .start();
            return sign.waitFor(30, TimeUnit.SECONDS) && sign.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
