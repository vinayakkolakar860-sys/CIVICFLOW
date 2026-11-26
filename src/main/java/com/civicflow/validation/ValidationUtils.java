package com.civicflow.validation;

import com.civicflow.exception.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.regex.Pattern;

/**
 * Centralized domain input validation helper methods.
 */
public final class ValidationUtils {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[+]?[0-9\\s()-]{7,20}$");
    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9_-]{2,30}$");
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");

    private ValidationUtils() {
    }

    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required.");
        }
        return value.trim();
    }

    public static String requireMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new ValidationException(fieldName + " cannot exceed " + maxLength + " characters.");
        }
        return value != null ? value.trim() : null;
    }

    public static String validateEmail(String email, String fieldName) {
        String trimmed = requireNonBlank(email, fieldName);
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Please enter a valid email address for " + fieldName + ".");
        }
        return trimmed.toLowerCase();
    }

    public static String validatePhone(String phone, String fieldName) {
        String trimmed = requireNonBlank(phone, fieldName);
        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Please enter a valid phone number (7–20 digits) for " + fieldName + ".");
        }
        return trimmed;
    }

    public static String validateCode(String code, String fieldName) {
        String trimmed = requireNonBlank(code, fieldName).toUpperCase();
        if (!CODE_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(fieldName + " must be 2–30 characters using uppercase letters, digits, hyphens, or underscores.");
        }
        return trimmed;
    }

    public static String validateUsername(String username) {
        String trimmed = requireNonBlank(username, "Username");
        if (!USERNAME_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("Username must be 3–50 characters (letters, digits, '.', '_', or '-').");
        }
        return trimmed;
    }

    public static void validateVoterDob(LocalDate dob, LocalDate referenceDate, int minAgeYears) {
        if (dob == null) {
            throw new ValidationException("Date of birth is required.");
        }
        if (dob.isAfter(referenceDate)) {
            throw new ValidationException("Date of birth cannot be in the future.");
        }
        int age = Period.between(dob, referenceDate).getYears();
        if (age < minAgeYears) {
            throw new ValidationException("Simulated voter must be at least " + minAgeYears + " years old.");
        }
        if (age > 120) {
            throw new ValidationException("Date of birth is outside the valid range.");
        }
    }

    public static void validateCandidateAge(int age) {
        if (age < 18 || age > 120) {
            throw new ValidationException("Candidate age must be between 18 and 120.");
        }
    }

    public static void validateElectionDateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new ValidationException("Both election start and end date/time are required.");
        }
        if (!end.isAfter(start)) {
            throw new ValidationException("Election end date/time must be strictly after start date/time.");
        }
    }
}
