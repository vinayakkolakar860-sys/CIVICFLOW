package com.civicflow.security;

import com.civicflow.exception.ValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and safely stores uploaded image files (candidate photos & fictional party logos).
 * Enforces file extension allow-list, magic byte inspection, size limits, and path-traversal protection.
 */
public class FileUploadValidator {

    public static final long MAX_IMAGE_SIZE_BYTES = 2L * 1024L * 1024L; // 2 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");

    public void validateImageFile(Path sourceFile) {
        if (sourceFile == null || !Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
            throw new ValidationException("Selected image file does not exist.");
        }

        String fileName = sourceFile.getFileName().toString();
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new ValidationException("Invalid file name detected.");
        }

        String ext = extractExtension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ValidationException("Unsupported image format. Only PNG and JPG/JPEG images are allowed.");
        }

        try {
            long size = Files.size(sourceFile);
            if (size <= 0) {
                throw new ValidationException("Image file is empty.");
            }
            if (size > MAX_IMAGE_SIZE_BYTES) {
                throw new ValidationException("Image file exceeds the maximum allowed size of 2 MB.");
            }
            if (!hasValidImageMagicBytes(sourceFile, ext)) {
                throw new ValidationException("File content does not match a valid PNG or JPEG image.");
            }
        } catch (IOException e) {
            throw new ValidationException("Unable to inspect uploaded image file.");
        }
    }

    /**
     * Validates and copies an image into a controlled uploads directory using a safe generated name.
     */
    public String storeValidatedImage(Path sourceFile, Path targetBaseDir, String categoryPrefix) {
        validateImageFile(sourceFile);
        String ext = extractExtension(sourceFile.getFileName().toString());
        String safePrefix = categoryPrefix.replaceAll("[^a-zA-Z0-9_-]", "");
        String safeFileName = safePrefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "." + ext;

        try {
            Path resolvedDir = targetBaseDir.toAbsolutePath().normalize();
            Files.createDirectories(resolvedDir);
            Path destination = resolvedDir.resolve(safeFileName).normalize();
            if (!destination.startsWith(resolvedDir)) {
                throw new ValidationException("Invalid upload target path.");
            }
            Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toString();
        } catch (IOException e) {
            throw new ValidationException("Failed to store uploaded image file.");
        }
    }

    private String extractExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private boolean hasValidImageMagicBytes(Path file, String ext) throws IOException {
        byte[] header = new byte[8];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.read(header);
            if (read < 3) return false;
        }
        if ("png".equals(ext)) {
            return (header[0] & 0xFF) == 0x89
                    && (header[1] & 0xFF) == 0x50
                    && (header[2] & 0xFF) == 0x4E
                    && (header[3] & 0xFF) == 0x47;
        }
        // JPEG magic bytes FF D8 FF
        return (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }
}
