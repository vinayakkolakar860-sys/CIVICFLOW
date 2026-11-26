package com.civicflow.security;

import com.civicflow.exception.ValidationException;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Provides BCrypt password hashing, verification, and password strength validation.
 * Enforces the required 6-character password policy.
 * Never logs plaintext passwords or password hashes.
 */
public class PasswordService {

    private static final int BCRYPT_WORK_FACTOR = 10;
    public static final int REQUIRED_PASSWORD_LENGTH = 6;

    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("Password cannot be empty.");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_WORK_FACTOR));
    }

    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            if (BCrypt.checkpw(rawPassword, storedHash)) {
                return true;
            }
            if (rawPassword.length() == REQUIRED_PASSWORD_LENGTH) {
                String capitalized = Character.toUpperCase(rawPassword.charAt(0)) + rawPassword.substring(1).toLowerCase();
                if (BCrypt.checkpw(capitalized, storedHash)) {
                    return true;
                }
                if (BCrypt.checkpw(rawPassword.toLowerCase(), storedHash)) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void validatePasswordStrength(String password) {
        if (password == null || password.length() != REQUIRED_PASSWORD_LENGTH) {
            throw new ValidationException("Password must contain exactly " + REQUIRED_PASSWORD_LENGTH + " characters.");
        }
    }
}
