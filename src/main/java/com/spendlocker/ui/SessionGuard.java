package com.spendlocker.ui;

import com.spendlocker.config.AppConfig;
import com.spendlocker.db.DatabaseManager;
import com.spendlocker.db.VaultLockedException;
import com.spendlocker.util.AlertUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/** Re-locks the vault after a period of inactivity and re-prompts for the master password in place. */
public class SessionGuard {

    private static final int DEFAULT_TIMEOUT_MINUTES = 5;
    private static SessionGuard activeInstance;

    private final PauseTransition idleTimer;
    private int suspendCount = 0;

    public SessionGuard(Scene scene) {
        idleTimer = new PauseTransition(Duration.minutes(timeoutMinutes()));
        idleTimer.setOnFinished(e -> lockNow());
        scene.addEventFilter(MouseEvent.ANY, this::resetTimer);
        scene.addEventFilter(KeyEvent.ANY, this::resetTimer);
        idleTimer.play();
        activeInstance = this;
    }

    /** Called from Settings after the timeout preference changes, so it applies without a restart. */
    public static void applyNewTimeout() {
        if (activeInstance != null) {
            activeInstance.restart();
        }
    }

    /**
     * Pauses auto-lock for the duration of a long operation the user has stepped away from the
     * app for (e.g. a browser-based OAuth consent flow) — matched with {@link #resumeAutoLock()}.
     * Nests safely: only the outermost suspend/resume pair actually stops/restarts the timer.
     */
    public static void suspendAutoLock() {
        if (activeInstance != null) {
            activeInstance.suspendCount++;
            activeInstance.idleTimer.stop();
        }
    }

    public static void resumeAutoLock() {
        if (activeInstance != null) {
            activeInstance.suspendCount = Math.max(0, activeInstance.suspendCount - 1);
            if (activeInstance.suspendCount == 0) {
                activeInstance.idleTimer.playFromStart();
            }
        }
    }

    private void resetTimer(Event event) {
        if (suspendCount == 0) {
            idleTimer.playFromStart();
        }
    }

    public void restart() {
        idleTimer.setDuration(Duration.minutes(timeoutMinutes()));
        idleTimer.playFromStart();
    }

    private void lockNow() {
        DatabaseManager.getInstance().close();
        while (true) {
            LoginResult result = LoginDialog.prompt();
            switch (result.type()) {
                case CANCELLED:
                    Platform.exit();
                    return;
                case ALREADY_UNLOCKED:
                    idleTimer.playFromStart();
                    return;
                case PASSWORD:
                    try {
                        DatabaseManager.getInstance().open(result.password());
                        idleTimer.playFromStart();
                        return;
                    } catch (VaultLockedException e) {
                        AlertUtil.error("Unable to unlock vault", e.getMessage());
                    }
                    break;
            }
        }
    }

    public static int timeoutMinutes() {
        try {
            String saved = new AppConfig().get(AppConfig.KEY_SESSION_TIMEOUT_MINUTES);
            return saved != null ? Integer.parseInt(saved) : DEFAULT_TIMEOUT_MINUTES;
        } catch (Exception e) {
            return DEFAULT_TIMEOUT_MINUTES;
        }
    }
}
