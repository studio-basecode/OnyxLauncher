package com.cannon.onyxlauncher.modloaders.modpacks.api;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

public final class ModpackUrlUtils {
    private ModpackUrlUtils() {}

    public static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String normalizeUrl(String url) {
        return normalizeUrl(url, null);
    }

    public static String normalizeUrl(String url, String baseUrl) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return "";

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (lower.contains("://")) {
            return trimmed;
        }

        if (!isBlank(baseUrl)) {
            try {
                return new URL(new URL(baseUrl), trimmed).toString();
            } catch (MalformedURLException ignored) {
                // Fall through to domain-like detection.
            }
        }

        if (trimmed.matches("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(:\\d+)?(/.*)?$")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    public static boolean isHttpUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
