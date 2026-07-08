package net.java.openjdk.cacio;

import java.security.PrivilegedAction;
import java.util.Properties;

public class GetPropCompatX implements PrivilegedAction<String> {
    private final String key;
    private final String defaultValue;

    public GetPropCompatX(String key) {
        this(key, null);
    }

    public GetPropCompatX(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    @Override
    public String run() {
        return defaultValue == null ? System.getProperty(key) : System.getProperty(key, defaultValue);
    }

    public static String privilegedGetProperty(String key) {
        return System.getProperty(key);
    }

    public static String privilegedGetProperty(String key, String defaultValue) {
        return System.getProperty(key, defaultValue);
    }

    public static Properties privilegedGetProperties() {
        return System.getProperties();
    }
}
