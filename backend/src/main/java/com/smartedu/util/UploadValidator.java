package com.smartedu.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

/**
 * Pre-write checks for uploaded files. All checks run before transferTo so a rejected file never reaches the upload directory.
 */
public final class UploadValidator {

    private static final byte[] PDF_SIGNATURE = {
            (byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46, (byte) 0x2D
    };

    private static final byte[] OLE2_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1
    };

    private static final byte[][] ZIP_SIGNATURES = {
            {(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04},
            {(byte) 0x50, (byte) 0x4B, (byte) 0x05, (byte) 0x06},
            {(byte) 0x50, (byte) 0x4B, (byte) 0x07, (byte) 0x08}
    };

    private static final Map<String, byte[][]> SIGNATURES = Map.of(
            "pdf", new byte[][]{PDF_SIGNATURE},
            "doc", new byte[][]{OLE2_SIGNATURE},
            // Legacy PPT/XLS use OLE2; their OOXML successors use the ZIP container.
            "ppt", new byte[][]{OLE2_SIGNATURE},
            "xls", new byte[][]{OLE2_SIGNATURE},
            "docx", ZIP_SIGNATURES,
            "pptx", ZIP_SIGNATURES,
            "xlsx", ZIP_SIGNATURES
    );

    private UploadValidator() {
    }

    /**
     * @return null when the file passes, otherwise the rejection code
     */
    public static String checkSize(MultipartFile file, long maxSizeBytes) {
        if (file == null || file.isEmpty()) {
            return "FILE_EMPTY";
        }
        if (file.getSize() > maxSizeBytes) {
            return "FILE_TOO_LARGE";
        }
        return null;
    }

    public static boolean isAllowedType(String extension, String allowedTypes) {
        if (extension == null || allowedTypes == null) {
            return false;
        }
        return Arrays.stream(allowedTypes.split(","))
                .map(String::trim)
                .anyMatch(extension::equalsIgnoreCase);
    }

    public static boolean headerMatches(MultipartFile file, String extension) throws IOException {
        if (file == null || file.isEmpty() || extension == null) {
            return false;
        }
        String ext = extension.toLowerCase();
        if ("md".equals(ext) || "markdown".equals(ext)) {
            return isReadableText(file);
        }
        byte[][] signatures = SIGNATURES.get(ext);
        if (signatures == null) {
            return false;
        }
        byte[] header = readHeader(file, OLE2_SIGNATURE.length);
        for (byte[] signature : signatures) {
            if (startsWith(header, signature)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readHeader(MultipartFile file, int length) throws IOException {
        try (InputStream stream = file.getInputStream()) {
            byte[] buffer = new byte[length];
            int total = 0;
            while (total < length) {
                int read = stream.read(buffer, total, length - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            return Arrays.copyOf(buffer, total);
        }
    }

    private static boolean startsWith(byte[] header, byte[] signature) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Markdown has no magic number; reject binary payloads that cannot be plain text.
     */
    private static boolean isReadableText(MultipartFile file) throws IOException {
        try (InputStream stream = file.getInputStream()) {
            byte[] sample = new byte[1024];
            int total = 0;
            while (total < sample.length) {
                int read = stream.read(sample, total, sample.length - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            if (total == 0) {
                return false;
            }
            int controlBytes = 0;
            for (int i = 0; i < total; i++) {
                byte value = sample[i];
                if (value < 0x20 && value != 0x09 && value != 0x0A && value != 0x0D) {
                    controlBytes++;
                }
            }
            return controlBytes * 100 / total < 10;
        }
    }
}
