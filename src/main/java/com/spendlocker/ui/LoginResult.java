package com.spendlocker.ui;

/** What {@link LoginDialog#prompt()} produced: a password to try, an already-open vault, or a cancel. */
public class LoginResult {

    public enum Type { PASSWORD, ALREADY_UNLOCKED, CANCELLED }

    private final Type type;
    private final String password;

    private LoginResult(Type type, String password) {
        this.type = type;
        this.password = password;
    }

    public static LoginResult password(String password) {
        return new LoginResult(Type.PASSWORD, password);
    }

    public static LoginResult alreadyUnlocked() {
        return new LoginResult(Type.ALREADY_UNLOCKED, null);
    }

    public static LoginResult cancelled() {
        return new LoginResult(Type.CANCELLED, null);
    }

    public Type type() {
        return type;
    }

    public String password() {
        return password;
    }
}
