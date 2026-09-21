package com.spendlocker.security;

import java.security.SecureRandom;

/** Generates a human-copyable recovery key: 5 groups of 4 chars from an unambiguous alphabet. */
public class RecoveryKeyGenerator {

    // No 0/O, 1/I/L — avoids characters that are easy to misread when written down.
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        StringBuilder sb = new StringBuilder();
        for (int group = 0; group < 5; group++) {
            if (group > 0) sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
        }
        return sb.toString();
    }
}
